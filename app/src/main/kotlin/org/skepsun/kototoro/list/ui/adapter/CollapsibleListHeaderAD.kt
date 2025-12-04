package org.skepsun.kototoro.list.ui.adapter

import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.databinding.ItemCollapsibleHeaderBinding
import org.skepsun.kototoro.list.ui.model.CollapsibleListHeader
import org.skepsun.kototoro.list.ui.model.ListModel

/**
 * Listener for collapsible header clicks
 */
interface CollapsibleHeaderClickListener {
    fun onCollapsibleHeaderClick(header: CollapsibleListHeader)
}

fun collapsibleListHeaderAD(
    listener: CollapsibleHeaderClickListener?,
) = adapterDelegateViewBinding<CollapsibleListHeader, ListModel, ItemCollapsibleHeaderBinding>(
    { inflater, parent -> ItemCollapsibleHeaderBinding.inflate(inflater, parent, false) },
) {

    if (listener != null) {
        binding.root.setOnClickListener {
            listener.onCollapsibleHeaderClick(item)
        }
    }

    bind { payloads ->
        if (payloads.isEmpty()) {
            // Full bind
            val headerText = item.getText(context)
            binding.textViewTitle.text = headerText
            
            // Update expand/collapse icon
            if (item.isCollapsible) {
                binding.imageViewExpand.visibility = View.VISIBLE
                val rotation = if (item.isExpanded) 180f else 0f
                binding.imageViewExpand.rotation = rotation
                
                // Accessibility: Update content description for screen readers
                val expandedState = if (item.isExpanded) {
                    context.getString(org.skepsun.kototoro.R.string.epub_cd_chapter_group_expanded)
                } else {
                    context.getString(org.skepsun.kototoro.R.string.epub_cd_chapter_group_collapsed)
                }
                binding.root.contentDescription = context.getString(
                    org.skepsun.kototoro.R.string.epub_cd_chapter_group_header,
                    headerText
                ) + ", " + expandedState
            } else {
                binding.imageViewExpand.visibility = View.GONE
                
                // Accessibility: Simple content description for non-collapsible headers
                binding.root.contentDescription = context.getString(
                    org.skepsun.kototoro.R.string.epub_cd_chapter_group_header,
                    headerText
                )
            }
        } else {
            // Partial bind with payloads
            val payload = payloads.firstOrNull()
            if (payload is Boolean) {
                // Animate the expand/collapse icon
                val fromRotation = if (payload) 0f else 180f
                val toRotation = if (payload) 180f else 0f
                val animation = RotateAnimation(
                    fromRotation, toRotation,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 200
                    fillAfter = true
                }
                binding.imageViewExpand.startAnimation(animation)
                
                // Accessibility: Update content description after state change
                val headerText = item.getText(context)
                val expandedState = if (item.isExpanded) {
                    context.getString(org.skepsun.kototoro.R.string.epub_cd_chapter_group_expanded)
                } else {
                    context.getString(org.skepsun.kototoro.R.string.epub_cd_chapter_group_collapsed)
                }
                binding.root.contentDescription = context.getString(
                    org.skepsun.kototoro.R.string.epub_cd_chapter_group_header,
                    headerText
                ) + ", " + expandedState
            }
        }
    }
}
