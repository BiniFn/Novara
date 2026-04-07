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
import org.skepsun.kototoro.core.ui.widgets.SwipeFilterPillView
import org.skepsun.kototoro.parsers.model.ContentType

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

	private var checkContentType: SwipeFilterPillView? = null
	private var checkTag: ImageView? = null
	private var customView: View? = null

	fun attachTo(fragment: Fragment) {
		fragment.addMenuProvider(this)
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		val item = menu.add(0, 8888, 0, "")
		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

		customView = LayoutInflater.from((callback as Fragment).requireContext())
			.inflate(R.layout.layout_searchbar_filters, null, false)
		
		item.actionView = customView

		checkContentType = customView?.findViewById(R.id.filter_content_type)
		checkTag = customView?.findViewById(R.id.filter_source_tag)

		checkContentType?.apply {
			val appSettings = dagger.hilt.android.EntryPointAccessors.fromApplication<org.skepsun.kototoro.core.ui.BaseActivityEntryPoint>(context.applicationContext).settings
			defaultType = appSettings.filterPillDefaultType
			swipeLeftType = appSettings.filterPillSwipeLeftType
			swipeRightType = appSettings.filterPillSwipeRightType
			
			val current = callback.getSelectedContentType()
			setCurrentType(when (current) {
				BrowseGroupTab.Novel -> ContentType.NOVEL
				BrowseGroupTab.Video -> ContentType.VIDEO
				else -> ContentType.MANGA
			})
			
			onFilterSelectedListener = { type ->
				val tab = when (type) {
					ContentType.NOVEL -> BrowseGroupTab.Novel
					ContentType.VIDEO -> BrowseGroupTab.Video
					else -> BrowseGroupTab.Content
				}
				callback.onContentTypeSelected(tab)
				updateIcons()
			}
		}

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
		checkContentType = null
		checkTag = null
	}

	private fun showContentTypePopup() {
		val checkContentTypeView = checkContentType ?: return
		val popup = PopupMenu(checkContentTypeView.context, checkContentTypeView, android.view.Gravity.END)
		val tabs = BrowseGroupTab.getAllTabs()
		val current = callback.getSelectedContentType()

		tabs.forEachIndexed { index, tab ->
			popup.menu.add(0, index, index, tab.titleRes).apply {
				setIcon(tab.iconRes)
				isCheckable = true
				isChecked = current == tab
				isEnabled = callback.isContentTypeEnabled(tab)
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
			tabs.getOrNull(item.itemId)?.let { tab ->
				callback.onContentTypeSelected(tab)
				updateIcons()
			}
			true
		}

		popup.show()
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

		checkContentType?.isVisible = showContent
		checkTag?.isVisible = showSource
	}

	fun updateIcons() {
		val selectedTab = callback.getSelectedContentType()
		val selectedTags = callback.getSelectedSourceTags()

		checkContentType?.let {
			it.setCurrentType(when (selectedTab) {
				BrowseGroupTab.Novel -> ContentType.NOVEL
				BrowseGroupTab.Video -> ContentType.VIDEO
				else -> ContentType.MANGA
			})
			it.isEnabled = callback.isContentTypeEnabled(selectedTab)
		}
		
		val iconRes = if (selectedTags.size == 1) {
			selectedTags.first().iconRes
		} else {
			callback.getSourceTagIconRes()
		}
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
