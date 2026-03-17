package org.skepsun.kototoro.list.ui.adapter

import android.view.View
import org.skepsun.kototoro.core.ui.widgets.TipView

interface ContentListListener : ContentDetailsClickListener, ListStateHolderListener, ListHeaderClickListener,
	TipView.OnButtonClickListener, QuickFilterClickListener {

	fun onFilterClick(view: View?)
}
