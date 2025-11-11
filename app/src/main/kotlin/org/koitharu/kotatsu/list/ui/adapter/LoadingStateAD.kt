package org.skepsun.kototoro.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import org.skepsun.kototoro.R
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState

fun loadingStateAD() = adapterDelegate<LoadingState, ListModel>(R.layout.item_loading_state) {
}