package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.databinding.ItemAvailableExtensionBinding
import org.skepsun.kototoro.databinding.ItemExtensionLanguageHeaderBinding
import org.skepsun.kototoro.databinding.ItemExtensionSectionHeaderBinding
import java.util.Locale

class ExtensionsBrowserAdapter(
	private val onToggleLanguageGroup: (ExtensionsBrowserListItem.LanguageHeader) -> Unit,
	private val onPrimaryAction: (ExtensionsBrowserListItem.Entry) -> Unit,
	private val onRemove: (ExtensionsBrowserListItem.Entry) -> Unit,
	private val onCancelInstall: (ExtensionsBrowserListItem.Entry) -> Unit,
) : ListAdapter<ExtensionsBrowserListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is ExtensionsBrowserListItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
		is ExtensionsBrowserListItem.LanguageHeader -> VIEW_TYPE_LANGUAGE_HEADER
		is ExtensionsBrowserListItem.Entry -> VIEW_TYPE_ENTRY
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(
				ItemExtensionSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
			)

			VIEW_TYPE_LANGUAGE_HEADER -> LanguageHeaderViewHolder(
				ItemExtensionLanguageHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
				onToggleLanguageGroup,
			)

			else -> EntryViewHolder(
				ItemAvailableExtensionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
				onPrimaryAction,
				onRemove,
				onCancelInstall,
			)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is ExtensionsBrowserListItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
			is ExtensionsBrowserListItem.LanguageHeader -> (holder as LanguageHeaderViewHolder).bind(item)
			is ExtensionsBrowserListItem.Entry -> (holder as EntryViewHolder).bind(item)
		}
	}

	fun getSpanSize(position: Int, spanCount: Int): Int {
		return when (getItem(position)) {
			is ExtensionsBrowserListItem.Entry -> 1
			else -> spanCount
		}
	}

	private class SectionHeaderViewHolder(
		private val binding: ItemExtensionSectionHeaderBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExtensionsBrowserListItem.SectionHeader) {
			binding.root.updatePaddingRelative(start = binding.root.paddingStart, end = binding.root.paddingEnd)
			binding.textTitle.text = binding.root.context.getString(item.section.titleRes)
			binding.textCount.text = item.count.toString()
		}
	}

	private class LanguageHeaderViewHolder(
		private val binding: ItemExtensionLanguageHeaderBinding,
		private val onToggleLanguageGroup: (ExtensionsBrowserListItem.LanguageHeader) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExtensionsBrowserListItem.LanguageHeader) {
			val locale = if (item.language.isBlank()) Locale.ROOT else item.language.toLocaleOrNull()
			val prefix = if (item.isCollapsed) "\u25b6" else "\u25bc"
			binding.textTitle.text = "$prefix ${locale.getDisplayName(binding.root.context)}"
			binding.textCount.text = item.count.toString()
			binding.root.setOnClickListener { onToggleLanguageGroup(item) }
		}
	}

	private class EntryViewHolder(
		private val binding: ItemAvailableExtensionBinding,
		private val onPrimaryAction: (ExtensionsBrowserListItem.Entry) -> Unit,
		private val onRemove: (ExtensionsBrowserListItem.Entry) -> Unit,
		private val onCancelInstall: (ExtensionsBrowserListItem.Entry) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExtensionsBrowserListItem.Entry) = with(binding) {
			textName.text = item.name
			textLanguage.text = when {
				item.language.isBlank() -> root.context.getString(R.string.multi_language_short)
				else -> item.language.uppercase()
			}
			textPackage.text = item.pkgName
			textVersion.text = item.versionName
			if (item.state == ExtensionsBrowserEntryState.INSTALLED) {
				val icon = runCatching { root.context.packageManager.getApplicationIcon(item.pkgName) }.getOrNull()
				if (icon != null) {
					imageIcon.disposeImage()
					imageIcon.setImageDrawable(icon)
				} else {
					imageIcon.setImageAsync(getFallbackIcon(item))
				}
			} else {
				if (item.extension.iconUrl.isBlank()) {
					imageIcon.setImageAsync(getFallbackIcon(item))
				} else {
					imageIcon.setImageAsync(item.extension.iconUrl)
				}
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
				ExtensionsBrowserEntryState.INSTALLING -> item.installProgressPercent?.let {
					root.context.getString(R.string.extension_download_progress, it)
				} ?: root.context.getString(R.string.extension_download_in_progress)
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
			buttonSecondary.isVisible = when (item.state) {
				ExtensionsBrowserEntryState.INSTALLING -> true
				ExtensionsBrowserEntryState.UPDATE_AVAILABLE,
				ExtensionsBrowserEntryState.UNTRUSTED,
				ExtensionsBrowserEntryState.INCOMPATIBLE -> true

				ExtensionsBrowserEntryState.AVAILABLE,
				ExtensionsBrowserEntryState.INSTALLED -> false
			}
			buttonSecondary.setImageResource(if (item.state == ExtensionsBrowserEntryState.INSTALLING) {
				R.drawable.ic_disable
			} else {
				R.drawable.ic_disable
			})
			buttonSecondary.isEnabled = true
			buttonSecondary.setOnClickListener {
				if (item.state == ExtensionsBrowserEntryState.INSTALLING) {
					onCancelInstall(item)
				} else {
					onRemove(item)
				}
			}
		}

		private fun getFallbackIcon(item: ExtensionsBrowserListItem.Entry): Int = when (item.extension.type) {
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.MIHON -> R.drawable.ic_source_mihon
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.ANIYOMI -> R.drawable.ic_source_aniyomi
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.IREADER -> R.drawable.ic_source_mihon // Fallback
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.JAR -> R.mipmap.ic_launcher
		}
	}

	private companion object {
		const val VIEW_TYPE_SECTION_HEADER = 0
		const val VIEW_TYPE_LANGUAGE_HEADER = 1
		const val VIEW_TYPE_ENTRY = 2

		val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExtensionsBrowserListItem>() {
			override fun areItemsTheSame(oldItem: ExtensionsBrowserListItem, newItem: ExtensionsBrowserListItem): Boolean {
				return when {
					oldItem is ExtensionsBrowserListItem.SectionHeader && newItem is ExtensionsBrowserListItem.SectionHeader -> {
						oldItem.section == newItem.section
					}

					oldItem is ExtensionsBrowserListItem.LanguageHeader && newItem is ExtensionsBrowserListItem.LanguageHeader -> {
						oldItem.section == newItem.section && oldItem.language == newItem.language
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
