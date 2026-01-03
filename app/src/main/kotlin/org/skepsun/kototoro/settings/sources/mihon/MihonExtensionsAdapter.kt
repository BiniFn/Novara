package org.skepsun.kototoro.settings.sources.mihon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemMihonExtensionBinding

/**
 * Adapter for displaying Mihon extensions in a RecyclerView.
 */
class MihonExtensionsAdapter : ListAdapter<MihonExtensionItem, MihonExtensionsAdapter.ViewHolder>(
    DIFF_CALLBACK
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMihonExtensionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemMihonExtensionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MihonExtensionItem) {
            val context = binding.root.context
            binding.textName.text = item.appName
            binding.textPackage.text = item.pkgName
            binding.textVersion.text = item.versionName
            binding.textLanguage.text = item.lang.uppercase()
            binding.textSourceCount.text = context.resources.getQuantityString(
                R.plurals.source_count, item.sourceCount, item.sourceCount
            )
            
            // Show NSFW badge
            binding.badgeNsfw.isVisible = item.isNsfw
            
            // Show source names
            binding.textSources.text = item.sourceNames.joinToString(", ")
            binding.textSources.isVisible = item.sourceNames.isNotEmpty()
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MihonExtensionItem>() {
            override fun areItemsTheSame(
                oldItem: MihonExtensionItem,
                newItem: MihonExtensionItem
            ): Boolean {
                return oldItem.pkgName == newItem.pkgName
            }

            override fun areContentsTheSame(
                oldItem: MihonExtensionItem,
                newItem: MihonExtensionItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
