package org.skepsun.kototoro.browser

interface BrowserCallback : OnHistoryChangedListener {

	fun onLoadingStateChanged(isLoading: Boolean)

	fun onTitleChanged(title: CharSequence, subtitle: CharSequence?)
}
