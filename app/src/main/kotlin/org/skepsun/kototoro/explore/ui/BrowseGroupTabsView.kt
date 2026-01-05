package org.skepsun.kototoro.explore.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ViewBrowseGroupTabsBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab

/**
 * Custom view for displaying browse group tabs.
 * 
 * This view shows a horizontal scrollable list of chips representing
 * different content groups (All, Manga, Novel, Video, JSON Sources).
 */
class BrowseGroupTabsView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
	
	private val binding: ViewBrowseGroupTabsBinding
	private var onTabSelectedListener: ((BrowseGroupTab) -> Unit)? = null
	private var tabs = BrowseGroupTab.getAllTabs()
	
	init {
		binding = ViewBrowseGroupTabsBinding.inflate(LayoutInflater.from(context), this, true)
		setupTabs()
	}
	
	/**
	 * Set the available tabs for this view.
	 */
	fun setTabs(newTabs: List<BrowseGroupTab>) {
		if (tabs == newTabs) return
		
		val currentSelection = getSelectedTab()
		tabs = newTabs
		setupTabs()
		
		// Try to restore selection or select "All"
		if (!setSelectedTab(currentSelection)) {
			setSelectedTab(BrowseGroupTab.All)
		}
	}
	
	private fun setupTabs() {
		binding.chipGroup.removeAllViews()
		
		tabs.forEach { tab ->
			val chip = createChip(tab)
			binding.chipGroup.addView(chip)
		}
		
		// Select the first tab (All) by default
		if (binding.chipGroup.childCount > 0) {
			(binding.chipGroup.getChildAt(0) as? Chip)?.isChecked = true
		}
		
		binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
			if (checkedIds.isNotEmpty()) {
				val checkedId = checkedIds.first()
				val chip = group.findViewById<Chip>(checkedId)
				val tab = chip?.tag as? BrowseGroupTab
				if (tab != null) {
					onTabSelectedListener?.invoke(tab)
				}
			}
		}
	}
	
	private fun createChip(tab: BrowseGroupTab): Chip {
		return Chip(context).apply {
			id = generateViewId()
			text = context.getString(tab.titleRes)
			tag = tab
			isCheckable = true
			
			// Compact visuals
			val density = resources.displayMetrics.density
			chipMinHeight = 28 * density
			minHeight = 0
			setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
			setEnsureMinTouchTargetSize(false)
			
			setChipBackgroundColorResource(R.color.selector_chip_background)
			setTextColor(context.getColorStateList(R.color.selector_chip_text))
		}
	}
	
	/**
	 * Set the listener for tab selection events.
	 */
	fun setOnTabSelectedListener(listener: (BrowseGroupTab) -> Unit) {
		onTabSelectedListener = listener
	}
	
	/**
	 * Get the currently selected tab.
	 */
	fun getSelectedTab(): BrowseGroupTab {
		val checkedId = binding.chipGroup.checkedChipId
		if (checkedId != ChipGroup.NO_ID) {
			val chip = binding.chipGroup.findViewById<Chip>(checkedId)
			return chip?.tag as? BrowseGroupTab ?: BrowseGroupTab.All
		}
		return BrowseGroupTab.All
	}
	
	/**
	 * Set the selected tab programmatically.
	 */
	fun setSelectedTab(tab: BrowseGroupTab): Boolean {
		for (i in 0 until binding.chipGroup.childCount) {
			val chip = binding.chipGroup.getChildAt(i) as? Chip
			if (chip?.tag == tab) {
				chip.isChecked = true
				return true
			}
		}
		return false
	}
}
