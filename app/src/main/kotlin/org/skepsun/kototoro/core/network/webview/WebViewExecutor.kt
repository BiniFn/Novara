package org.skepsun.kototoro.core.network.webview

import android.content.Context
import android.util.AndroidRuntimeException
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.annotation.MainThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.exceptions.CloudFlareException
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.network.proxy.ProxyProvider
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.parser.tvbox.TVBoxPlayback
import org.skepsun.kototoro.core.parser.legado.LegadoNetworkUtils
import org.skepsun.kototoro.core.util.ext.configureForParser
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Cookie
import java.util.concurrent.TimeUnit

@Singleton
class WebViewExecutor @Inject constructor(
	@ApplicationContext private val context: Context,
	private val proxyProvider: ProxyProvider,
	private val cookieJar: MutableCookieJar,
	private val mangaRepositoryFactoryProvider: Provider<ContentRepository.Factory>,
) {

	private var webViewCached: WeakReference<WebView>? = null
	private val mutex = Mutex()

	val defaultUserAgent: String? by lazy {
		try {
			WebSettings.getDefaultUserAgent(context)
		} catch (e: AndroidRuntimeException) {
			e.printStackTraceDebug()
			// Probably WebView is not available
			null
		}
	}

	suspend fun evaluateJs(baseUrl: String?, script: String): String? = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				if (!baseUrl.isNullOrEmpty()) {
					suspendCoroutine { cont ->
						webView.webViewClient = ContinuationResumeWebViewClient(cont)
						webView.loadDataWithBaseURL(baseUrl, " ", "text/html", null, null)
					}
				}
				suspendCoroutine { cont ->
					webView.evaluateJavascript(script) { result ->
						cont.resume(result?.takeUnless { it == "null" })
					}
				}
			} finally {
				webView.reset()
			}
		}
	}

	suspend fun tryResolveCaptcha(exception: CloudFlareException, timeout: Long): Boolean = mutex.withLock {
		runCatchingCancellable {
			withContext(Dispatchers.Main.immediate) {
				val webView = obtainWebView()
				try {
					exception.source.getUserAgent()?.let {
						webView.settings.userAgentString = it
					}
					withTimeout(timeout) {
						suspendCancellableCoroutine { cont ->
							webView.webViewClient = CaptchaContinuationClient(
								cookieJar = cookieJar,
								targetUrl = exception.url,
								continuation = cont,
							)
							webView.loadUrl(exception.url)
						}
					}
				} finally {
					webView.reset()
				}
			}
		}.onFailure { e ->
			exception.addSuppressed(e)
			e.printStackTraceDebug()
		}.isSuccess
	}

	/**
	 * Load a URL via WebView and return the page HTML after JavaScript execution.
	 * Used for sources that require webView: true.
	 *
	 * @param url The URL to load
	 * @param headers Optional headers to set
	 * @param delayMs Delay in milliseconds to wait after page load for JS execution
	 * @param timeoutMs Total timeout in milliseconds
	 * @param webJs Optional custom JavaScript to execute instead of outerHTML
	 * @param blockImages Whether to block images to speed up loading
	 * @return The page HTML content (or JS result)
	 */
	suspend fun loadPageHtml(
		url: String,
		headers: Map<String, String>? = null,
		delayMs: Long = 2500,
		timeoutMs: Long = 60000,
		webJs: String? = null,
		blockImages: Boolean = true
	): String = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				// Configure with common browser settings plus image blocking
				webView.configureForParser(headers?.get("User-Agent"), blockImages = blockImages)

				withTimeout(timeoutMs) {
					// Load the page and wait for it to finish
					suspendCancellableCoroutine<Unit> { cont ->
						webView.webViewClient = object : WebViewClient() {
							override fun onPageFinished(view: WebView?, loadedUrl: String?) {
								if (cont.isActive) {
									cont.resume(Unit)
								}
							}
						}
						if (headers != null && headers.isNotEmpty()) {
							webView.loadUrl(url, headers)
						} else {
							webView.loadUrl(url)
						}
					}

					// Wait for initial JavaScript to execute
					kotlinx.coroutines.delay(delayMs)
					
					val extractionJs = webJs?.takeIf { it.isNotBlank() } ?: "document.documentElement.outerHTML"
					
					// Poll for the actual content to be available (some sites use anti-adblock that takes time)
					// Match Legado's retry mechanism: up to 30 attempts
					var result = ""
					var attempts = 0
					val maxAttempts = 30
					while (attempts < maxAttempts) {
						result = suspendCancellableCoroutine<String> { cont ->
							webView.evaluateJavascript(extractionJs) { jsResult ->
								val unescaped = jsResult?.let {
									if (it == "null") ""
									else if (it.startsWith("\"") && it.endsWith("\"")) {
										// Basic JSON unescaping for the string result
										it.substring(1, it.length - 1)
											.replace("\\u003C", "<")
											.replace("\\u003E", ">")
											.replace("\\n", "\n")
											.replace("\\t", "\t")
											.replace("\\\"", "\"")
											.replace("\\\\", "\\")
									} else it
								} ?: ""
								cont.resume(unescaped)
							}
						}
						
						// If user provided custom JS, we don't know the "ready" condition, just return it
						if (webJs != null && webJs.isNotBlank()) break
						
						// Default extraction: Check if content element has actual text
						val hasContent = suspendCancellableCoroutine<Boolean> { cont ->
							webView.evaluateJavascript(
								"""(function() {
									var el = document.getElementById('TextContent') || document.querySelector('#TextContent') || document.querySelector('.content') || document.querySelector('#content');
									if (!el) return false;
									var text = el.innerText || el.textContent || '';
									return text.trim().length > 100;
								})()"""
							) { jsResult ->
								cont.resume(jsResult == "true")
							}
						}
						
						if (hasContent) {
							android.util.Log.d("WebViewExecutor", "[WebView] Content ready after ${attempts + 1} attempts")
							break
						}
						
						attempts++
						if (attempts < maxAttempts) {
							android.util.Log.d("WebViewExecutor", "[WebView] Content not ready, waiting... (attempt $attempts/$maxAttempts)")
							kotlinx.coroutines.delay(1000)
						}
					}
					
					android.util.Log.d("WebViewExecutor", "[WebView] Extracted length=${result.length}, preview=${result.take(200).replace("\n", " ")}")
					result
				}
			} finally {
				webView.reset()
			}
		}
	}

	suspend fun sniffMediaUrl(
		url: String,
		headers: Map<String, String>? = null,
		delayMs: Long = 2500,
		timeoutMs: Long = 20000,
	): SniffedMediaResult? = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				webView.configureForParser(headers?.get(CommonHeaders.USER_AGENT), blockImages = true)
				withTimeout(timeoutMs) {
					suspendCancellableCoroutine { cont ->
						val pageFinished = AtomicBoolean(false)
						val candidateUrl = AtomicReference<String?>(null)

						fun tryResume(result: SniffedMediaResult?) {
							if (cont.isActive) {
								cont.resume(result)
							}
						}

						fun captureCandidate(rawUrl: String?) {
							val normalized = rawUrl?.takeIf(TVBoxPlayback::looksLikeDirectPlaybackUrl) ?: return
							if (candidateUrl.compareAndSet(null, normalized)) {
								val mergedHeaders = headers.orEmpty().toMutableMap()
								if (!mergedHeaders.keys.any { it.equals(CommonHeaders.REFERER, ignoreCase = true) }) {
									mergedHeaders[CommonHeaders.REFERER] = url
								}
								CookieManager.getInstance().getCookie(normalized)?.takeIf { it.isNotBlank() }?.let { cookie ->
									mergedHeaders[CommonHeaders.COOKIE] = cookie
								}
								tryResume(SniffedMediaResult(url = normalized, headers = mergedHeaders))
							}
						}

						webView.webViewClient = object : WebViewClient() {
							override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
								captureCandidate(request?.url?.toString())
								return null
							}

							override fun onPageFinished(view: WebView?, loadedUrl: String?) {
								if (!pageFinished.compareAndSet(false, true)) {
									return
								}
								kotlinx.coroutines.CoroutineScope(cont.context).launch(Dispatchers.Main.immediate) {
									kotlinx.coroutines.delay(delayMs)
									if (!cont.isActive || candidateUrl.get() != null) {
										return@launch
									}
									val html = suspendCancellableCoroutine<String> { htmlCont ->
										webView.evaluateJavascript("document.documentElement.outerHTML") { jsResult ->
											htmlCont.resume(decodeJavascriptString(jsResult))
										}
									}
									val embeddedUrl = TVBoxPlayback.extractEmbeddedMediaUrl(html)
									if (embeddedUrl != null) {
										captureCandidate(embeddedUrl)
									} else {
										tryResume(null)
									}
								}
							}
						}
						if (!headers.isNullOrEmpty()) {
							webView.loadUrl(url, headers)
						} else {
							webView.loadUrl(url)
						}
					}
				}
			} finally {
				webView.reset()
			}
		}
	}

	private suspend fun obtainWebView(): WebView {
		webViewCached?.get()?.let {
			return it
		}
		return withContext(Dispatchers.Main.immediate) {
			webViewCached?.get()?.let {
				return@withContext it
			}
			WebView(context).also {
				it.configureForParser(null)
				webViewCached = WeakReference(it)
				proxyProvider.applyWebViewConfig()
				it.onResume()
				it.resumeTimers()
			}
		}
	}

	private fun ContentSource.getUserAgent(): String? {
		val repository = mangaRepositoryFactoryProvider.get().create(this) as? ParserContentRepository
		return repository?.getRequestHeaders()?.get(CommonHeaders.USER_AGENT)
	}

	suspend fun loginAndCheck(
		loginUrl: String,
		checkStatus: suspend (url: String, title: String) -> Boolean,
		onSuccess: (() -> Unit)? = null,
		cookiesDomain: String? = null,
		timeoutMs: Long = TimeUnit.SECONDS.toMillis(20),
	): Boolean = mutex.withLock {
		return runCatching {
			withContext(Dispatchers.Main.immediate) {
				val webView = obtainWebView()
				try {
					val result = withTimeout(timeoutMs) {
						suspendCancellableCoroutine<Boolean> { cont ->
							webView.webViewClient = object : WebViewClient() {
								override fun onPageFinished(view: WebView?, url: String?) {
									val currentUrl = url ?: ""
									val title = view?.title ?: ""
									kotlinx.coroutines.CoroutineScope(cont.context).launch {
										val ok = runCatching { checkStatus(currentUrl, title) }.getOrDefault(false)
										if (ok && cont.isActive) {
											cont.resume(true)
										}
									}
								}
							}
							webView.loadUrl(loginUrl)
						}
					}
					if (!result) return@withContext false
						val domain = cookiesDomain ?: loginUrl.toHttpUrlOrNull()?.host ?: return@withContext true
						val rootDomain = LegadoNetworkUtils.getSubDomain("https://$domain")
						// 同步 WebView Cookie 到应用 CookieJar
						cookieJar.removeCookies(loginUrl.toHttpUrlOrNull() ?: return@withContext true) { true }
						android.webkit.CookieManager.getInstance().getCookie(loginUrl)?.let { raw ->
							val httpUrl = "https://$rootDomain".toHttpUrlOrNull() ?: return@let
							raw.split(";").map { it.trim() }.forEach { line ->
								val parts = line.split("=", limit = 2)
								if (parts.size == 2) {
									val name = parts[0]
									val value = parts[1]
									val c = runCatching {
										Cookie.Builder()
											.domain(httpUrl.host)
											.path("/")
											.name(name)
											.value(value)
											.secure()
											.build()
									}.getOrNull()
								if (c != null) {
									cookieJar.saveFromResponse(httpUrl, listOf(c))
								}
							}
						}
					}
					onSuccess?.invoke()
					true
				} finally {
					webView.reset()
				}
			}
		}.getOrDefault(false)
	}

	@MainThread
	private fun WebView.reset() {
		stopLoading()
		webViewClient = WebViewClient()
		settings.userAgentString = defaultUserAgent
		loadDataWithBaseURL(null, " ", "text/html", null, null)
		clearHistory()
	}

	data class SniffedMediaResult(
		val url: String,
		val headers: Map<String, String>,
	)

	private fun decodeJavascriptString(value: String?): String {
		if (value.isNullOrBlank() || value == "null") {
			return ""
		}
		if (!value.startsWith("\"") || !value.endsWith("\"")) {
			return value
		}
		return value.substring(1, value.length - 1)
			.replace("\\u003C", "<")
			.replace("\\u003E", ">")
			.replace("\\u0026", "&")
			.replace("\\n", "\n")
			.replace("\\t", "\t")
			.replace("\\\"", "\"")
			.replace("\\\\", "\\")
	}
}
