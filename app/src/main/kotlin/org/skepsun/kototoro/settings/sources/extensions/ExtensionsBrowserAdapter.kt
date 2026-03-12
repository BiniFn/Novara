package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemAvailableExtensionBinding
import org.skepsun.kototoro.databinding.ItemExtensionSectionHeaderBinding

class ExtensionsBrowserAdapter(
	private val onPrimaryAction: (ExtensionsBrowserListItem.Entry) -> Unit,
	private val onRemove: (ExtensionsBrowserListItem.Entry) -> Unit,
) : ListAdapter<ExtensionsBrowserListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is ExtensionsBrowserListItem.Header -> VIEW_TYPE_HEADER
		is ExtensionsBrowserListItem.Entry -> VIEW_TYPE_ENTRY
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_HEADER -> HeaderViewHolder(
				ItemExtensionSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
			)

			else -> EntryViewHolder(
				ItemAvailableExtensionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
				onPrimaryAction,
				onRemove,
			)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is ExtensionsBrowserListItem.Header -> (holder as HeaderViewHolder).bind(item)
			is ExtensionsBrowserListItem.Entry -> (holder as EntryViewHolder).bind(item)
		}
	}

	private class HeaderViewHolder(
		private val binding: ItemExtensionSectionHeaderBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExtensionsBrowserListItem.Header) {
			binding.textTitle.text = binding.root.context.getString(item.section.titleRes)
			binding.textCount.text = item.count.toString()
		}
	}

	private class EntryViewHolder(
		private val binding: ItemAvailableExtensionBinding,
		private val onPrimaryAction: (ExtensionsBrowserListItem.Entry) -> Unit,
		private val onRemove: (ExtensionsBrowserListItem.Entry) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExtensionsBrowserListItem.Entry) = with(binding) {
			textName.text = item.name
			textLanguage.text = item.language.uppercase()
			textPackage.text = item.pkgName
			textVersion.text = item.versionName
			if (item.extension.iconUrl.isBlank()) {
				imageIcon.setImageAsync(getFallbackIcon(item))
			} else {
				imageIcon.setImageAsync(item.extension.iconUrl)
			}
			textRepo.text = if (item.state == ExtensionsBrowserEntryState.INSTALLED) {
				root.context.getString(R.string.installed_repo_label)
			} else {
				item.repoLabel
			}
			textSourceCount.text = root.context.getString(R.string.extension_source_count, item.sourceNames.size)
			textSources.text = item.sourceNames.joinToString(", ")
			badgeNsfw.isVisible = item.isNsfw
			textInstalledVersion.text = item.installedVersionName?.let { root.context.getString(R.string.installed_version_pattern, it) }
			textInstalledVersion.isVisible = item.installedVersionName != null && item.state != ExtensionsBrowserEntryState.INSTALLED
			buttonPrimary.text = when (item.state) {
				ExtensionsBrowserEntryState.AVAILABLE -> root.context.getString(R.string.install_extension)
				ExtensionsBrowserEntryState.UPDATE_AVAILABLE -> root.context.getString(R.string.update_extension)
				ExtensionsBrowserEntryState.INSTALLED -> root.context.getString(R.string.remove)
				ExtensionsBrowserEntryState.INSTALLING -> root.context.getString(R.string.installing_extension)
				ExtensionsBrowserEntryState.UNTRUSTED -> root.context.getString(R.string.details)
				ExtensionsBrowserEntryState.INCOMPATIBLE -> root.context.getString(R.string.details)
			}
			buttonPrimary.isEnabled = when (item.state) {
				ExtensionsBrowserEntryState.AVAILABLE,
				ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
				ExtensionsBrowserEntryState.INSTALLED,
				ExtensionsBrowserEntryState.UNTRUSTED,
				ExtensionsBrowserEntryState.INCOMPATIBLE -> true

				ExtensionsBrowserEntryState.INSTALLING -> false
			}
			buttonPrimary.setOnClickListener {
				when (item.state) {
					ExtensionsBrowserEntryState.AVAILABLE,
					ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
					ExtensionsBrowserEntryState.UNTRUSTED,
					ExtensionsBrowserEntryState.INCOMPATIBLE -> onPrimaryAction(item)

					ExtensionsBrowserEntryState.INSTALLED -> onRemove(item)
					ExtensionsBrowserEntryState.INSTALLING -> Unit
				}
			}
			buttonSecondary.isVisible = item.installedVersionName != null && when (item.state) {
				ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
				ExtensionsBrowserEntryState.UNTRUSTED,
				ExtensionsBrowserEntryState.INCOMPATIBLE -> true

				ExtensionsBrowserEntryState.AVAILABLE,
				ExtensionsBrowserEntryState.INSTALLED,
				ExtensionsBrowserEntryState.INSTALLING -> false
			}
			buttonSecondary.text = root.context.getString(R.string.remove)
			buttonSecondary.isEnabled = item.state != ExtensionsBrowserEntryState.INSTALLING
			buttonSecondary.setOnClickListener { onRemove(item) }
		}

		private fun getFallbackIcon(item: ExtensionsBrowserListItem.Entry): Int = when (item.extension.type) {
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.MIHON -> R.drawable.ic_source_mihon
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.ANIYOMI -> R.drawable.ic_source_aniyomi
		}
	}

	private companion object {
		const val VIEW_TYPE_HEADER = 0
		const val VIEW_TYPE_ENTRY = 1

		val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExtensionsBrowserListItem>() {
			override fun areItemsTheSame(oldItem: ExtensionsBrowserListItem, newItem: ExtensionsBrowserListItem): Boolean {
				return when {
					oldItem is ExtensionsBrowserListItem.Header && newItem is ExtensionsBrowserListItem.Header -> {
						oldItem.section == newItem.section
					}

					oldItem is ExtensionsBrowserListItem.Entry && newItem is ExtensionsBrowserListItem.Entry -> {
						oldItem.pkgName == newItem.pkgName && oldItem.state == newItem.state
					}

					else -> false
				}
			}

			override fun areContentsTheSame(oldItem: ExtensionsBrowserListItem, newItem: ExtensionsBrowserListItem): Boolean {
				return oldItem == newItem
			}
		}
	}
}
