package org.skepsun.kototoro.main.ui

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag

/**
 * MenuProvider that adds a 2x2 grid of filter icons to the SearchBar toolbar.
 *
 * Each Fragment implements [Callback] to customize which items are visible
 * and how selections are handled.
 */
class SearchBarFilterViewController(
	private val callback: Callback,
) : MenuProvider {

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
		fun getSourceTagIconRes(): Int = R.drawable.ic_filter_menu
		/** Return true to consume the click and prevent default popup */
		fun onFilterIconClicked(anchor: View): Boolean = false
	}

	private var checkManga: ImageView? = null
	private var checkNovel: ImageView? = null
	private var checkVideo: ImageView? = null
	private var checkTag: ImageView? = null
	private var customView: View? = null

	fun attachTo(fragment: Fragment) {
		fragment.addMenuProvider(this)
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		val item = menu.add(0, 8888, 0, "")
		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

		customView = LayoutInflater.from((callback as Fragment).requireContext())
			.inflate(R.layout.layout_searchbar_filters_2x2, null, false)
		
		item.actionView = customView

		checkManga = customView?.findViewById(R.id.filter_content_manga)
		checkNovel = customView?.findViewById(R.id.filter_content_novel)
		checkVideo = customView?.findViewById(R.id.filter_content_video)
		checkTag = customView?.findViewById(R.id.filter_source_tag)

		checkManga?.setOnClickListener { toggleContentType(BrowseGroupTab.Content) }
		checkNovel?.setOnClickListener { toggleContentType(BrowseGroupTab.Novel) }
		checkVideo?.setOnClickListener { toggleContentType(BrowseGroupTab.Video) }
		checkTag?.setOnClickListener {
			if (!callback.onFilterIconClicked(checkTag!!)) {
				showSourceTagPopup()
			}
		}

		updateVisibility()
		updateIcons()
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

	fun destroy() {
		// Android automatically removes action views when menu is destroyed/cleared.
		customView = null
		checkManga = null
		checkNovel = null
		checkVideo = null
		checkTag = null
	}

	private fun toggleContentType(tab: BrowseGroupTab) {
		val current = callback.getSelectedContentType()
		callback.onContentTypeSelected(if (current == tab) BrowseGroupTab.All else tab)
		updateIcons()
	}

	private fun showSourceTagPopup() {
		val checkTagView = checkTag ?: return
		val popup = PopupMenu(checkTagView.context, checkTagView, android.view.Gravity.END)
		val entries = callback.getSourceTagEntries()
		val selectedTags = callback.getSelectedSourceTags()

		// "All" option
		popup.menu.add(0, -1, 0, R.string.all).apply {
			isCheckable = true
			isChecked = selectedTags.isEmpty()
		}

		entries.forEachIndexed { index, tag ->
			popup.menu.add(0, index, index + 1, tag.getPopupTitle(checkTagView)).apply {
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
		}

		popup.setOnMenuItemClickListener { item ->
			if (item.itemId == -1) {
				callback.onSourceTagSelected(null)
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
		val showContent = callback.isContentTypeFilterVisible()
		val showSource = callback.isSourceTagFilterVisible()

		checkManga?.isVisible = showContent
		checkNovel?.isVisible = showContent
		checkVideo?.isVisible = showContent
		checkTag?.isVisible = showSource
	}

	fun updateIcons() {
		val selectedTab = callback.getSelectedContentType()
		val selectedTags = callback.getSelectedSourceTags()

		checkManga?.let { tintItem(it, selectedTab == BrowseGroupTab.Content, callback.isContentTypeEnabled(BrowseGroupTab.Content)) }
		checkNovel?.let { tintItem(it, selectedTab == BrowseGroupTab.Novel, callback.isContentTypeEnabled(BrowseGroupTab.Novel)) }
		checkVideo?.let { tintItem(it, selectedTab == BrowseGroupTab.Video, callback.isContentTypeEnabled(BrowseGroupTab.Video)) }
		
		val iconRes = callback.getSourceTagIconRes()
		checkTag?.setImageResource(iconRes)
		checkTag?.let { tintItem(it, selectedTags.isNotEmpty(), true) }
	}

	private fun tintItem(icon: ImageView, isSelected: Boolean, isEnabled: Boolean) {
		val context = icon.context
		val color = when {
			!isEnabled -> MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0).let { c ->
				android.graphics.Color.argb(97, android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
			}
			isSelected -> MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
			else -> MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
		}
		icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
		icon.isEnabled = isEnabled
	}
}
