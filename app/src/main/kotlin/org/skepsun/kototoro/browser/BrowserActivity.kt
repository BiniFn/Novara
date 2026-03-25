package org.skepsun.kototoro.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.InteractiveActionRequiredException
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.parsers.model.ContentSource

@AndroidEntryPoint
class BrowserActivity : BaseBrowserActivity() {

	private var pendingResult = RESULT_CANCELED
	private var successCookieUrl: String? = null
	private var successCookieName: String? = null
	private var initialSuccessCookieValue: String? = null

	override fun onCreate2(savedInstanceState: Bundle?, source: ContentSource, repository: ParserContentRepository?) {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		viewBinding.webView.webViewClient = BrowserClient(this, adBlock)
		successCookieUrl = intent?.getStringExtra(AppRouter.KEY_SUCCESS_COOKIE_URL)
		successCookieName = intent?.getStringExtra(AppRouter.KEY_SUCCESS_COOKIE_NAME)
		initialSuccessCookieValue = getSuccessCookieValue()
		logCookieState("open", initialSuccessCookieValue)
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				e.printStackTraceDebug()
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null) {
				val url = intent?.dataString
				if (url.isNullOrEmpty()) {
					finishAfterTransition()
				} else {
					onTitleChanged(
						intent?.getStringExtra(AppRouter.KEY_TITLE) ?: getString(R.string.loading_),
						url,
					)
					viewBinding.webView.loadUrl(url)
				}
			}
		}
	}

	override fun finish() {
		val currentValue = getSuccessCookieValue()
		logCookieState("finish", currentValue)
		pendingResult = if (isSuccessCookieSatisfied(currentValue)) RESULT_OK else RESULT_CANCELED
		setResult(pendingResult)
		super.finish()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)
		menuInflater.inflate(R.menu.opt_browser, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_browser -> {
			if (!router.openExternalBrowser(viewBinding.webView.url.orEmpty(), item.title)) {
				Snackbar.make(viewBinding.webView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
			}
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	class Contract : ActivityResultContract<InteractiveActionRequiredException, Boolean>() {
		override fun createIntent(
			context: Context,
			input: InteractiveActionRequiredException
		): Intent = AppRouter.browserIntent(
			context = context,
			url = input.url,
			source = input.source,
			title = null,
		).apply {
			input.userAgent?.let {
				putExtra(AppRouter.KEY_USER_AGENT, it)
			}
			input.successCookieUrl?.let {
				putExtra(AppRouter.KEY_SUCCESS_COOKIE_URL, it)
			}
			input.successCookieName?.let {
				putExtra(AppRouter.KEY_SUCCESS_COOKIE_NAME, it)
			}
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == RESULT_OK
	}

	private fun isSuccessCookieSatisfied(currentValue: String? = null): Boolean {
		val cookieUrl = successCookieUrl ?: return true
		val cookieName = successCookieName ?: return true
		runCatching { CookieManager.getInstance().flush() }
		val resolvedCurrentValue = currentValue ?: getCookieValue(cookieUrl, cookieName)
		val isSatisfied = !resolvedCurrentValue.isNullOrEmpty() && resolvedCurrentValue != initialSuccessCookieValue
		android.util.Log.d(
			TAG,
			"success_check url=$cookieUrl cookie=$cookieName initial=${maskCookieValue(initialSuccessCookieValue)} current=${maskCookieValue(resolvedCurrentValue)} passed=$isSatisfied",
		)
		return isSatisfied
	}

	private fun getSuccessCookieValue(): String? {
		val cookieUrl = successCookieUrl ?: return null
		val cookieName = successCookieName ?: return null
		return getCookieValue(cookieUrl, cookieName)
	}

	private fun getCookieValue(url: String, name: String): String? {
		val raw = CookieManager.getInstance().getCookie(url) ?: return null
		return raw.split(';')
			.asSequence()
			.map { it.trim() }
			.firstOrNull { it.startsWith("$name=") }
			?.substringAfter('=')
			?.takeIf { it.isNotEmpty() }
	}

	private fun logCookieState(stage: String, cookieValue: String?) {
		val cookieUrl = successCookieUrl ?: return
		val cookieName = successCookieName ?: return
		val rawCookie = CookieManager.getInstance().getCookie(cookieUrl)
		android.util.Log.d(
			TAG,
			"cookie_state stage=$stage url=$cookieUrl cookie=$cookieName value=${maskCookieValue(cookieValue)} hasCookie=${!cookieValue.isNullOrEmpty()} rawHasCfClearance=${rawCookie?.contains("$cookieName=") == true}",
		)
	}

	private fun maskCookieValue(value: String?): String {
		if (value.isNullOrEmpty()) return "<empty>"
		return if (value.length <= 8) "***" else "${value.take(4)}...${value.takeLast(4)}"
	}

	companion object {

		const val TAG = "BrowserActivity"
	}
}
