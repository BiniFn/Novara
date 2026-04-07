package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemInstalledExtensionBinding

class InstalledExtensionsAdapter(private val onItemClick: (InstalledExtensionItem) -> Unit) :
	ListAdapter<InstalledExtensionItem, InstalledExtensionsAdapter.ViewHolder>(DIFF_CALLBACK) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemInstalledExtensionBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false,
		)
		return ViewHolder(binding, onItemClick)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		private val binding: ItemInstalledExtensionBinding,
		private val onItemClick: (InstalledExtensionItem) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: InstalledExtensionItem) {
			binding.root.setOnClickListener { onItemClick(item) }
			val context = binding.root.context
			binding.textName.text = item.appName
			binding.textPackage.text = item.pkgName
			binding.textVersion.text = item.versionName
			binding.textLanguage.text = item.lang.uppercase()
			binding.textSourceCount.text = context.resources.getQuantityString(
				R.plurals.source_count,
				item.sourceCount,
				item.sourceCount,
			)
			binding.badgeNsfw.isVisible = item.isNsfw
			binding.textSources.text = item.sourceNames.joinToString(", ")
			binding.textSources.isVisible = item.sourceNames.isNotEmpty()
		}
	}

	companion object {
		private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<InstalledExtensionItem>() {
			override fun areItemsTheSame(
				oldItem: InstalledExtensionItem,
				newItem: InstalledExtensionItem,
			): Boolean {
				return oldItem.pkgName == newItem.pkgName
			}

			override fun areContentsTheSame(
				oldItem: InstalledExtensionItem,
				newItem: InstalledExtensionItem,
			): Boolean {
				return oldItem == newItem
			}
		}
	}
}
