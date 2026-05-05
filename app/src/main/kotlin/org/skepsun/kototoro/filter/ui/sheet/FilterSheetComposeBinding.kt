package org.skepsun.kototoro.filter.ui.sheet

import android.content.Context
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.viewbinding.ViewBinding

class FilterSheetComposeBinding private constructor(
    val composeView: ComposeView,
) : ViewBinding {

    override fun getRoot(): ComposeView = composeView

    companion object {
        fun inflate(context: Context): FilterSheetComposeBinding {
            val composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                id = android.R.id.content
            }
            return FilterSheetComposeBinding(composeView)
        }
    }
}
