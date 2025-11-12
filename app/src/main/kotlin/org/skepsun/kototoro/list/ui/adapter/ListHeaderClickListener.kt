package org.skepsun.kototoro.list.ui.adapter

import android.view.View
import org.skepsun.kototoro.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
