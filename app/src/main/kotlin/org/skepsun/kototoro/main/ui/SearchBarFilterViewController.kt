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
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
		fun getSourceTagEntries(): List<SourceTag> = SourceTag.quickFilterEntries
		fun isContentTypeFilterVisible(): Boolean = true
		fun isSourceTagFilterVisible(): Boolean = true
		fun isLanguagePresetFilterVisible(): Boolean = true
		fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean = true
		fun isSourceTagEnabled(tag: SourceTag): Boolean = true
		fun getSourceTagIconRes(): Int = R.drawable.ic_filter_menu
		/** Return true to consume the click and prevent default popup */
		fun onFilterIconClicked(anchor: View): Boolean = false
		fun onLanguagePresetClicked(anchor: View) {}
	}

	private var checkContentType: SwipeFilterPillView? = null
	private var checkTag: ImageView? = null
	private var checkLanguagePreset: ImageView? = null
	private var customView: View? = null

	fun attachTo(fragment: Fragment) {
		fragment.addSupportMenuProvider(this)
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		val item = menu.add(0, 8888, 0, "")
		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

		customView = LayoutInflater.from((callback as Fragment).requireContext())
			.inflate(R.layout.layout_searchbar_filters, null, false)
		
		item.actionView = customView

		checkContentType = customView?.findViewById(R.id.filter_content_type)
		checkTag = customView?.findViewById(R.id.filter_source_tag)
		checkLanguagePreset = customView?.findViewById(R.id.filter_language_preset)

		checkContentType?.apply {
			val appSettings = dagger.hilt.android.EntryPointAccessors.fromApplication<org.skepsun.kototoro.core.ui.BaseActivityEntryPoint>(context.applicationContext).settings
			defaultType = appSettings.filterPillDefaultType
			swipeLeftType = appSettings.filterPillSwipeLeftType
			swipeRightType = appSettings.filterPillSwipeRightType
			
			val current = callback.getSelectedContentType()
			setCurrentType(when (current) {
				BrowseGroupTab.Novel -> ContentType.NOVEL
				BrowseGroupTab.Video -> ContentType.VIDEO
				BrowseGroupTab.Content -> ContentType.MANGA
				BrowseGroupTab.All -> null
				else -> null
			})
			
			onFilterSelectedListener = { type ->
				val tab = when (type) {
					ContentType.NOVEL -> BrowseGroupTab.Novel
					ContentType.VIDEO -> BrowseGroupTab.Video
					ContentType.MANGA -> BrowseGroupTab.Content
					null -> BrowseGroupTab.All
					else -> BrowseGroupTab.All
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

		checkLanguagePreset?.setOnClickListener {
			if (!callback.onFilterIconClicked(checkLanguagePreset!!)) {
				showLanguagePresetPopup()
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
		checkLanguagePreset = null
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

	private fun showLanguagePresetPopup() {
		val anchor = checkLanguagePreset ?: return
		val popup = PopupMenu(anchor.context, anchor, android.view.Gravity.END)
		
		val context = anchor.context.applicationContext
		val baseAppEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication<org.skepsun.kototoro.core.BaseApp.BaseAppEntryPoint>(context)
		val appSettings = baseAppEntryPoint.settings()
		val presetDao = baseAppEntryPoint.database().get().getSourcePresetsDao()
		
		// Use coroutine to load presets and show popup
		org.skepsun.kototoro.core.util.ext.processLifecycleScope.launch(Dispatchers.IO) {
			val presets = presetDao.findAll()
			withContext(Dispatchers.Main) {
				// Manage presets option
				val manageItem = popup.menu.add(0, -2, 0, R.string.manage_preset_sources)
				manageItem.setIcon(R.drawable.ic_settings)
				
				// All / Default option
				val allItem = popup.menu.add(0, -1, 1, R.string.all)
				allItem.isCheckable = true
				allItem.isChecked = appSettings.activeSourcePresetId == -1L

				presets.forEachIndexed { index, preset ->
					val presetItem = popup.menu.add(0, index, index + 2, preset.title)
					presetItem.isCheckable = true
					presetItem.isChecked = appSettings.activeSourcePresetId == preset.presetId
				}

				// Force icons
				try {
					val field = popup.javaClass.getDeclaredField("mPopup")
					field.isAccessible = true
					val menuPopupHelper = field.get(popup)
					menuPopupHelper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
						.invoke(menuPopupHelper, true)
				} catch (_: Exception) {}

				popup.setOnMenuItemClickListener { item ->
					if (item.itemId == -2) {
						anchor.context.startActivity(android.content.Intent(anchor.context, org.skepsun.kototoro.explore.ui.preset.SourcePresetListActivity::class.java))
					} else {
						val presetId = if (item.itemId == -1) -1L else presets[item.itemId].presetId
						appSettings.activeSourcePresetId = presetId
						// Let observers handle UI updates
					}
					true
				}

				popup.show()
			}
		}
	}

	private fun SourceTag.getPopupTitle(anchorView: View): CharSequence {
		val title = anchorView.context.getString(titleRes)
		return BidiFormatter.getInstance().unicodeWrap(title, TextDirectionHeuristicsCompat.LTR)
	}

	fun updateVisibility() {
		val showContent = callback.isContentTypeFilterVisible()
		val showSource = callback.isSourceTagFilterVisible()
		val showPreset = callback.isLanguagePresetFilterVisible()

		checkContentType?.isVisible = showContent
		checkTag?.isVisible = showSource
		checkLanguagePreset?.isVisible = showPreset
	}

	fun updateIcons() {
		val selectedTab = callback.getSelectedContentType()
		val selectedTagsRaw = callback.getSelectedSourceTags()
		val validSelectedTags = selectedTagsRaw.intersect(callback.getSourceTagEntries().toSet())

		checkContentType?.let {
			it.setCurrentType(when (selectedTab) {
				BrowseGroupTab.Novel -> ContentType.NOVEL
				BrowseGroupTab.Video -> ContentType.VIDEO
				BrowseGroupTab.Content -> ContentType.MANGA
				BrowseGroupTab.All -> null
				else -> null
			})
			it.isEnabled = callback.isContentTypeEnabled(selectedTab)
		}
		
		val iconRes = if (validSelectedTags.size == 1) {
			validSelectedTags.first().iconRes
		} else {
			callback.getSourceTagIconRes()
		}
		checkTag?.setImageResource(iconRes)
		checkTag?.let { tintItem(it, validSelectedTags.isNotEmpty(), true) }
		checkLanguagePreset?.let { tintItem(it, false, true) }
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
