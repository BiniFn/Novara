package org.skepsun.kototoro.main.ui

import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.google.android.material.color.MaterialColors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag

/**
 * MenuProvider that adds content type toggle icons and a source tag dropdown
 * to the SearchBar toolbar, replacing the separate filter chip bars.
 *
 * Each Fragment implements [Callback] to customize which items are visible
 * and how selections are handled.
 */
class SearchBarFilterMenuProvider(
	private val callback: Callback,
	private val anchorView: View,
) : MenuProvider {

	private var menu: Menu? = null

	interface Callback {
		fun onContentTypeSelected(tab: BrowseGroupTab)
		fun onSourceTagSelected(tag: SourceTag?)
		fun getSelectedContentType(): BrowseGroupTab
		fun getSelectedSourceTags(): Set<SourceTag>
		fun getSourceTagEntries(): List<SourceTag> = SourceTag.entries.toList()
		fun isContentTypeFilterVisible(): Boolean = true
		fun isSourceTagFilterVisible(): Boolean = true
		fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean = true
		fun isSourceTagEnabled(tag: SourceTag): Boolean = true
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.menu_searchbar_filter, menu)
		this.menu = menu
		updateVisibility()
		updateIcons()
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.filter_content_manga -> {
				toggleContentType(BrowseGroupTab.Content)
				true
			}
			R.id.filter_content_novel -> {
				toggleContentType(BrowseGroupTab.Novel)
				true
			}
			R.id.filter_content_video -> {
				toggleContentType(BrowseGroupTab.Video)
				true
			}
			R.id.filter_source_tag -> {
				showSourceTagPopup()
				true
			}
			else -> false
		}
	}

	private fun toggleContentType(tab: BrowseGroupTab) {
		val current = callback.getSelectedContentType()
		callback.onContentTypeSelected(if (current == tab) BrowseGroupTab.All else tab)
		updateIcons()
	}

	private fun showSourceTagPopup() {
		val popup = PopupMenu(anchorView.context, anchorView, android.view.Gravity.END)
		val entries = callback.getSourceTagEntries()
		val selectedTags = callback.getSelectedSourceTags()

		// "All" option
		popup.menu.add(0, -1, 0, R.string.all).apply {
			isCheckable = true
			isChecked = selectedTags.isEmpty()
		}

		entries.forEachIndexed { index, tag ->
			popup.menu.add(0, index, index + 1, tag.getPopupTitle(anchorView)).apply {
				setIcon(tag.iconRes)
				isCheckable = true
				isChecked = tag in selectedTags
				isEnabled = callback.isSourceTagEnabled(tag)
			}
		}

		// Show icons in popup
		try {
			val field = popup.javaClass.getDeclaredField("mPopup")
			field.isAccessible = true
			val menuPopupHelper = field.get(popup)
			menuPopupHelper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
				.invoke(menuPopupHelper, true)
		} catch (_: Exception) {
			// Fallback: icons won't show, but titles will
		}

		popup.setOnMenuItemClickListener { item ->
			if (item.itemId == -1) {
				callback.onSourceTagSelected(null) // Clear selection
			} else {
				entries.getOrNull(item.itemId)?.let { tag ->
					callback.onSourceTagSelected(tag)
				}
			}
			updateIcons()
			true
		}

		popup.show()
	}

	private fun SourceTag.getPopupTitle(anchorView: View): CharSequence {
		val title = anchorView.context.getString(titleRes)
		return BidiFormatter.getInstance().unicodeWrap(title, TextDirectionHeuristicsCompat.LTR)
	}

	fun updateVisibility() {
		val m = menu ?: return
		val showContent = callback.isContentTypeFilterVisible()
		val showSource = callback.isSourceTagFilterVisible()

		m.findItem(R.id.filter_content_manga)?.isVisible = showContent
		m.findItem(R.id.filter_content_novel)?.isVisible = showContent
		m.findItem(R.id.filter_content_video)?.isVisible = showContent
		m.findItem(R.id.filter_source_tag)?.isVisible = showSource
	}

	fun updateIcons() {
		val m = menu ?: return
		val selectedTab = callback.getSelectedContentType()
		val selectedTags = callback.getSelectedSourceTags()

		tintItem(m.findItem(R.id.filter_content_manga), selectedTab == BrowseGroupTab.Content, callback.isContentTypeEnabled(BrowseGroupTab.Content))
		tintItem(m.findItem(R.id.filter_content_novel), selectedTab == BrowseGroupTab.Novel, callback.isContentTypeEnabled(BrowseGroupTab.Novel))
		tintItem(m.findItem(R.id.filter_content_video), selectedTab == BrowseGroupTab.Video, callback.isContentTypeEnabled(BrowseGroupTab.Video))
		tintItem(m.findItem(R.id.filter_source_tag), selectedTags.isNotEmpty(), true)
	}

	private fun tintItem(item: MenuItem?, isSelected: Boolean, isEnabled: Boolean) {
		val icon = item?.icon ?: return
		val context = anchorView.context

		val color = when {
			!isEnabled -> MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0).let { c ->
				android.graphics.Color.argb(97, android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
			}
			isSelected -> MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
			else -> MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
		}
		icon.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN)
		item.isEnabled = isEnabled
	}
}
