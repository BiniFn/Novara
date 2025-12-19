package org.skepsun.kototoro.settings.sources

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.MangaSource
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.parser.CachingMangaRepository
import org.skepsun.kototoro.core.parser.JsMangaRepository
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.ParserMangaRepository
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.prefs.SourceSettings
import org.skepsun.kototoro.core.js.JSSourceParser
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.explore.data.MangaSourcesRepository
import org.skepsun.kototoro.parsers.MangaParserAuthProvider
import org.skepsun.kototoro.parsers.MangaParserCredentialsAuthProvider
import org.skepsun.kototoro.parsers.exception.AuthRequiredException
import org.skepsun.kototoro.parsers.exception.ParseException
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import org.skepsun.kototoro.core.util.ext.EventFlow
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.skepsun.kototoro.core.network.webview.WebViewExecutor
import kotlinx.coroutines.withContext

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	private val cookieJar: MutableCookieJar,
	private val mangaSourcesRepository: MangaSourcesRepository,
	private val jsSourceParser: JSSourceParser,
	private val webViewExecutor: WebViewExecutor,
) : BaseViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {

	private val initialSource = MangaSource(savedStateHandle.get<String>(AppRouter.KEY_SOURCE))
	val repository = mangaRepositoryFactory.create(initialSource)
	val source = repository.source

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val username = MutableStateFlow<String?>(null)
	val isAuthorized = MutableStateFlow<Boolean?>(null)
	val browserUrl = MutableStateFlow<String?>(null)
	val isEnabled = mangaSourcesRepository.observeIsEnabled(source)
	private val _jsAccountMeta = MutableStateFlow<JsMangaRepository.JsAccountMeta?>(null)
	val jsAccountMeta: StateFlow<JsMangaRepository.JsAccountMeta?> = _jsAccountMeta.asStateFlow()
	private val _jsLoginState = MutableEventFlow<Boolean>()
	val jsLoginState: EventFlow<Boolean> = _jsLoginState
	private val _jsWebLoginState = MutableEventFlow<Boolean>()
	val jsWebLoginState: EventFlow<Boolean> = _jsWebLoginState
	private var jsWebLoginJob: Job? = null
	private var usernameLoadJob: Job? = null

	init {
		when (repository) {
			is ParserMangaRepository -> {
				browserUrl.value = "https://${repository.domain}"
				repository.getConfig().subscribe(this)
				loadUsername(repository.getAuthProvider())
			}
			is org.skepsun.kototoro.core.parser.dynamic.BasicJsonRepository -> {
				val url = runCatching {
					val json = Json { ignoreUnknownKeys = true; isLenient = true }
					val config = json.decodeFromString<LegadoBookSource>(
						(repository.source as JsonMangaSource).entity.config
					)
					config.bookSourceUrl.takeIf { it.isNotBlank() }
				}.getOrNull()
				browserUrl.value = url
			}
			is JsMangaRepository -> {
				val url = runCatching {
					val config = (repository.source as? JsonMangaSource)?.entity?.config ?: return@runCatching null
					jsSourceParser.parseMetadata(config).getOrNull()?.homepage?.takeIf { it.isNotBlank() }
				}.getOrNull()
				browserUrl.value = url
				loadJsAccountMeta(repository)
			}
		}
	}

	override fun onCleared() {
		when (repository) {
			is ParserMangaRepository -> {
				repository.getConfig().unsubscribe(this)
			}
		}
		super.onCleared()
	}

	private fun loadJsAccountMeta(repo: JsMangaRepository) {
		launchLoadingJob(Dispatchers.Default) {
			runCatching { repo.getJsAccountMeta() }
				.onSuccess { _jsAccountMeta.value = it }
		}
	}

	fun loginJs(username: String, password: String) {
		val repo = repository as? JsMangaRepository ?: return
		launchLoadingJob(Dispatchers.IO) {
			val ok = runCatching { repo.jsLogin(username, password) }.getOrDefault(false)
			_jsLoginState.call(ok)
			if (ok && repository is CachingMangaRepository) {
				(repository as CachingMangaRepository).invalidateCache()
			}
		}
	}

	fun logoutJs() {
		val repo = repository as? JsMangaRepository ?: return
		launchLoadingJob(Dispatchers.IO) {
			val ok = runCatching { repo.jsLogout() }.getOrDefault(false)
			_jsLoginState.call(!ok)
			browserUrl.value?.toHttpUrlOrNull()?.let { cookieJar.removeCookies(it) { true } }
			if (repository is CachingMangaRepository) {
				(repository as CachingMangaRepository).invalidateCache()
			}
		}
	}

	fun setJsCookies(values: Map<String, String>): Boolean {
		val targetUrl = browserUrl.value ?: return false
		val httpUrl = targetUrl.toHttpUrlOrNull() ?: return false
		val cookies = values.mapNotNull { (name, value) ->
			if (name.isBlank() || value.isBlank()) return@mapNotNull null
			runCatching {
				Cookie.Builder()
					.hostOnlyDomain(httpUrl.host)
					.path("/")
					.name(name.trim())
					.value(value)
					.apply { if (httpUrl.isHttps) secure() }
					.build()
			}.getOrNull()
		}
		if (cookies.isEmpty()) return false
		cookieJar.removeCookies(httpUrl) { true }
		cookieJar.saveFromResponse(httpUrl, cookies)
		return true
	}

	suspend fun loginJsWithWebview(): Boolean {
		jsWebLoginJob?.cancel()
		val repo = repository as? JsMangaRepository ?: return false
		val meta = jsAccountMeta.value ?: repo.getJsAccountMeta() ?: return false
		val loginUrl = meta.webLoginUrl ?: return false
		jsWebLoginJob = launchLoadingJob(Dispatchers.IO) {
			val ok = runCatching {
				webViewExecutor.loginAndCheck(
					loginUrl = loginUrl,
					checkStatus = { urlNow, titleNow ->
						repo.jsCheckWebLogin(urlNow, titleNow)
					},
					onSuccess = {
						kotlinx.coroutines.runBlocking {
							runCatching { repo.jsNotifyWebLoginSuccess() }.getOrDefault(false)
						}
					},
					cookiesDomain = null,
				)
			}.getOrDefault(false)
			_jsWebLoginState.call(ok)
			if (ok && repository is CachingMangaRepository) {
				(repository as CachingMangaRepository).invalidateCache()
			}
		}
		return true
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (repository is CachingMangaRepository) {
			if (key != SourceSettings.KEY_SLOWDOWN && key != SourceSettings.KEY_SORT_ORDER) {
				repository.invalidateCache()
			}
		}
		if (repository is ParserMangaRepository) {
			if (key == SourceSettings.KEY_DOMAIN) {
				browserUrl.value = "https://${repository.domain}"
			}
		}
	}

	fun onResume() {
		if (usernameLoadJob?.isActive != true && repository is ParserMangaRepository) {
			loadUsername(repository.getAuthProvider())
		}
	}

	fun clearCookies() {
		if (repository !is ParserMangaRepository) return
		launchLoadingJob(Dispatchers.Default) {
			val url = HttpUrl.Builder()
				.scheme("https")
				.host(repository.domain)
				.build()
			cookieJar.removeCookies(url, null)
			onActionDone.call(ReversibleAction(R.string.cookies_cleared, null))
			loadUsername(repository.getAuthProvider())
		}
	}

	fun setEnabled(value: Boolean) {
		launchJob(Dispatchers.Default) {
			mangaSourcesRepository.setSourcesEnabled(setOf(source), value)
		}
	}

    private fun loadUsername(authProvider: MangaParserAuthProvider?) {
        launchLoadingJob(Dispatchers.Default) {
            try {
                username.value = null
                isAuthorized.value = null
                isAuthorized.value = authProvider?.isAuthorized()
                username.value = authProvider?.getUsername()
            } catch (_: AuthRequiredException) {
                // 未登录或登录过期，保持为空，不上报错误
            } catch (_: ParseException) {
                // 用户名解析失败（例如站点不返回用户名或需要站点域 Cookie），不影响授权状态展示
            }
        }
    }

    fun loginByCredentials(username: String, password: String) {
        val authProvider = (repository as? ParserMangaRepository)?.getAuthProvider() as? MangaParserCredentialsAuthProvider
        if (authProvider == null) return
        launchLoadingJob(Dispatchers.IO) {
            val success = authProvider.login(username, password)
            // 登录成功后刷新授权状态与用户名展示
            val refreshed = (repository as? ParserMangaRepository)?.getAuthProvider()
            isAuthorized.value = success
            if (success) {
                // 先用输入的 username 进行乐观更新，避免解析失败导致界面报错
                this@SourceSettingsViewModel.username.value = username
            }
            loadUsername(refreshed)
        }
    }
}
