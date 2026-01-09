package org.skepsun.kototoro.core.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ViewSelectActionBarBinding

/**
 * A bottom action bar for batch operations on selected items.
 * 
 * Features:
 * - Count display showing selected/total items
 * - Select all button
 * - Invert selection button
 * - Main action button (e.g., Delete)
 * - Overflow menu for additional actions
 */
class SelectActionBar @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

	private val binding = ViewSelectActionBarBinding.inflate(
		LayoutInflater.from(context), this, true
	)
	
	private var callback: Callback? = null
	private var popupMenu: PopupMenu? = null
	private var isAllSelected = false

	init {
		if (!isInEditMode) {
			binding.btnSelectAll.setOnClickListener {
				isAllSelected = !isAllSelected
				callback?.selectAll(isAllSelected)
			}
			
			binding.btnInvertSelection.setOnClickListener {
				callback?.revertSelection()
			}
			
			binding.btnMainAction.setOnClickListener {
				callback?.onClickMainAction()
			}
			
			binding.btnMoreMenu.setOnClickListener {
				popupMenu?.show()
			}
		}
	}

	/**
	 * Sets the main action button text.
	 */
	fun setMainActionText(text: String) {
		binding.btnMainAction.text = text
	}

	/**
	 * Sets the main action button text from resource.
	 */
	fun setMainActionText(@StringRes resId: Int) {
		binding.btnMainAction.setText(resId)
	}

	/**
	 * Inflates a menu for the overflow button.
	 * @return The inflated menu for further customization
	 */
	fun inflateMenu(@MenuRes resId: Int): Menu? {
		popupMenu = PopupMenu(context, binding.btnMoreMenu).apply {
			inflate(resId)
		}
		return popupMenu?.menu
	}

	/**
	 * Sets the callback for action bar events.
	 */
	fun setCallback(callback: Callback) {
		this.callback = callback
	}

	/**
	 * Sets menu item click listener.
	 */
	fun setOnMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener) {
		popupMenu?.setOnMenuItemClickListener(listener)
	}

	/**
	 * Updates the count display.
	 * @param selectCount Number of selected items
	 * @param allCount Total number of items
	 */
	fun updateCount(selectCount: Int, allCount: Int) {
		binding.tvCount.text = selectCount.toString()
		binding.tvCountAll.text = context.getString(R.string.select_count_format, allCount)
		
		isAllSelected = selectCount >= allCount && allCount > 0
		
		// Enable/disable buttons based on selection
		val hasSelection = selectCount > 0
		binding.btnInvertSelection.isEnabled = hasSelection
		binding.btnMainAction.isEnabled = hasSelection
		binding.btnMoreMenu.isEnabled = hasSelection
	}

	/**
	 * Callback interface for SelectActionBar events.
	 */
	interface Callback {
		/**
		 * Called when select all button is clicked.
		 * @param selectAll True if all items should be selected
		 */
		fun selectAll(selectAll: Boolean)

		/**
		 * Called when invert selection button is clicked.
		 */
		fun revertSelection()

		/**
		 * Called when main action button is clicked.
		 */
		fun onClickMainAction()
	}
}
