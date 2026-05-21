package org.skepsun.kototoro.browser.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.skepsun.kototoro.R
import org.skepsun.kototoro.browser.BaseBrowserActivity
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.exceptions.resolve.CaptchaAutoResolveCoordinator
import org.skepsun.kototoro.core.exceptions.resolve.CaptchaHandler
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import javax.inject.Inject

@AndroidEntryPoint
class CloudFlareActivity : BaseBrowserActivity(), CloudFlareCallback {

	private var pendingResult = RESULT_CANCELED
	private val isHidden: Boolean by lazy { intent?.getBooleanExtra(EXTRA_HIDDEN, false) == true }
	private val isAutoResolve: Boolean by lazy { intent?.getBooleanExtra(EXTRA_AUTO_RESOLVE, false) == true }
	private var resultNotified = false
	private var hiddenTimeoutJob: Job? = null

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	@Inject
	lateinit var captchaAutoResolveCoordinator: CaptchaAutoResolveCoordinator

	private lateinit var cfClient: CloudFlareClient

	override fun onCreate2(savedInstanceState: Bundle?, source: ContentSource, repository: ParserContentRepository?) {
		if (isHidden) {
			supportActionBar?.hide()
			viewBinding.appbar.isVisible = false
			viewBinding.progressBar.isVisible = false
			viewBinding.root.alpha = 0f
			window.addFlags(
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			)
			hiddenTimeoutJob = lifecycleScope.launch {
				delay(HIDDEN_TIMEOUT_MS)
				viewBinding.webView.stopLoading()
				finishAfterTransition()
			}
		} else {
			setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		}
		val url = intent?.dataString
		if (url.isNullOrEmpty()) {
			finishAfterTransition()
			return
		}
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			cfClient = if (shouldUseInterception(repository)) {
				CloudFlareInterceptClient(cookieJar, this@CloudFlareActivity, adBlock, url)
			} else {
				CloudFlareClient(cookieJar, this@CloudFlareActivity, adBlock, url)
			}
			viewBinding.webView.webViewClient = cfClient
			if (savedInstanceState == null) {
				onTitleChanged(getString(R.string.loading_), url)
				viewBinding.webView.loadUrl(url)
			}
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		if (isHidden) {
			return false
		}
		menuInflater.inflate(R.menu.opt_captcha, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_retry -> {
			restartCheck()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun finish() {
		hiddenTimeoutJob?.cancel()
		setResult(pendingResult)
		if (isAutoResolve && !resultNotified) {
			resultNotified = true
			intent?.getStringExtra(AppRouter.KEY_SOURCE)?.let { sourceName ->
				captchaAutoResolveCoordinator.notifyResolveResult(
					org.skepsun.kototoro.core.model.ContentSource(sourceName),
					pendingResult == RESULT_OK,
				)
			}
		}
		super.finish()
	}

	override fun onLoadingStateChanged(isLoading: Boolean) = Unit

	override fun onPageLoaded() {
		if (!isHidden) {
			viewBinding.progressBar.isInvisible = true
		}
	}

	override fun onLoopDetected() {
		restartCheck()
	}

	override fun onCheckPassed() {
		pendingResult = RESULT_OK
		lifecycleScope.launch {
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(org.skepsun.kototoro.core.model.ContentSource(source))
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			finishAfterTransition()
		}
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		setTitle(title)
		supportActionBar?.subtitle = subtitle?.toString()?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }
	}

	private fun restartCheck() {
		lifecycleScope.launch {
			viewBinding.webView.stopLoading()
			yield()
			cfClient.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				clearCfCookies(targetUrl)
				viewBinding.webView.loadUrl(targetUrl.toString())
			}
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.Default) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	private suspend fun shouldUseInterception(repository: ParserContentRepository?): Boolean {
		if (repository == null) {
			return false
		}
		val key = repository.getConfigKeys()
			.filterIsInstance<ConfigKey.InterceptCloudflare>()
			.firstOrNull()
			?: return false
		return repository.getConfig()[key]
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
			return resultCode == RESULT_OK
		}
	}

	companion object {

		const val TAG = "CloudFlareActivity"
		const val EXTRA_HIDDEN = "hidden"
		const val EXTRA_AUTO_RESOLVE = "auto_resolve"
		private const val HIDDEN_TIMEOUT_MS = 15_000L
	}
}
