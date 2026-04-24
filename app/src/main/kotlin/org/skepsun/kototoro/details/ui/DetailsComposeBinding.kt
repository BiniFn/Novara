package org.skepsun.kototoro.details.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.viewbinding.ViewBinding

class DetailsComposeBinding private constructor(
    private val composeView: ComposeView,
) : ViewBinding {

    override fun getRoot(): ComposeView = composeView

    companion object {
        fun inflate(context: Context): DetailsComposeBinding {
            val composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                id = android.R.id.content
            }
            return DetailsComposeBinding(composeView)
        }
    }
}
