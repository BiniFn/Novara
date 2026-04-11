package org.skepsun.kototoro.main.ui

import android.view.View
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.main.ui.MainActivity

/**
 * Controller that bridges fragment filter state to the global Compose TopBar.
 * Exposes the exact same interface as before so fragments do not need to be rewritten.
 */
class SearchBarFilterViewController(
	private val callback: Callback,
) {
	interface Callback {
		fun onContentTypeSelected(tab: BrowseGroupTab)
		fun onSourceTagSelected(tag: SourceTag?)
		fun getSelectedContentType(): BrowseGroupTab
		fun getSelectedSourceTags(): Set<SourceTag>
		fun getSourceTagEntries(): List<SourceTag> = SourceTag.quickFilterEntries
		fun isContentTypeFilterVisible(): Boolean = true
		fun isSourceTagFilterVisible(): Boolean = true
		fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean = true
		fun isSourceTagEnabled(tag: SourceTag): Boolean = true
		fun getSourceTagIconRes(): Int = 0
		/** Return true to consume the click and prevent default popup */
		fun onFilterIconClicked(anchor: View): Boolean = false
	}

	private var attachedFragment: Fragment? = null

	fun attachTo(fragment: Fragment) {
		attachedFragment = fragment
		val activity = fragment.activity as? MainActivity
		activity?.setActiveFilterCallback(callback)
		updateIcons()
	}

	fun updateVisibility() {
		updateIcons()
	}

	fun updateIcons() {
		val activity = attachedFragment?.activity as? MainActivity
		activity?.refreshFilters()
	}

	fun destroy() {
		val activity = attachedFragment?.activity as? MainActivity
		activity?.clearActiveFilterCallback(callback)
		attachedFragment = null
	}
}
