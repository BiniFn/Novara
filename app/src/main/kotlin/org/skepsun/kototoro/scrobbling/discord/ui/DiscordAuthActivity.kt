package org.skepsun.kototoro.scrobbling.discord.ui

import android.os.Bundle
import android.view.MenuItem
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.browser.BaseBrowserActivity
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

@AndroidEntryPoint
class DiscordAuthActivity : BaseBrowserActivity(), DiscordTokenWebClient.Callback {

	@Inject
	lateinit var settings: AppSettings

	override fun onCreate2(
		savedInstanceState: Bundle?,
		source: ContentSource,
		repository: ParserContentRepository?
	) {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		viewBinding.webView.settings.userAgentString = USER_AGENT
		viewBinding.webView.webViewClient = DiscordTokenWebClient(this)
		if (savedInstanceState == null) {
			viewBinding.webView.loadUrl(BASE_URL)
		}
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun onTokenObtained(token: String) {
		settings.discordToken = token
		setResult(RESULT_OK)
		finish()
	}

	private companion object {

		const val BASE_URL = "https://discord.com/login"
		private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"
	}
}
