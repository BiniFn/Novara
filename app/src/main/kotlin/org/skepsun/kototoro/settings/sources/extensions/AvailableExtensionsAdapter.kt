package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemAvailableExtensionBinding

class AvailableExtensionsAdapter(
	private val onInstall: (AvailableExtensionListItem) -> Unit,
) : ListAdapter<AvailableExtensionListItem, AvailableExtensionsAdapter.ViewHolder>(DIFF_CALLBACK) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemAvailableExtensionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding, onInstall)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		private val binding: ItemAvailableExtensionBinding,
		private val onInstall: (AvailableExtensionListItem) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: AvailableExtensionListItem) = with(binding) {
			val extension = item.extension
			textName.text = extension.name
			textLanguage.text = extension.lang.uppercase()
			textPackage.text = extension.pkgName
			textVersion.text = extension.versionName
			if (extension.iconUrl.isBlank()) {
				imageIcon.setImageAsync(getFallbackIcon(extension.type))
			} else {
				imageIcon.setImageAsync(extension.iconUrl)
			}
			textRepo.text = extension.repoName
			textSourceCount.text = root.context.getString(R.string.extension_source_count, extension.sourceNames.size)
			textSources.text = extension.sourceNames.joinToString(", ")
			badgeNsfw.isVisible = extension.isNsfw
			buttonPrimary.text = when (item.state) {
				AvailableExtensionState.AVAILABLE -> root.context.getString(R.string.install_extension)
				AvailableExtensionState.UPDATE_AVAILABLE -> root.context.getString(R.string.update_extension)
				AvailableExtensionState.INSTALLED -> root.context.getString(R.string.installed_extension)
				AvailableExtensionState.INSTALLING -> root.context.getString(R.string.installing_extension)
			}
			buttonPrimary.isEnabled = item.state == AvailableExtensionState.AVAILABLE || item.state == AvailableExtensionState.UPDATE_AVAILABLE
			buttonSecondary.isVisible = false
			textInstalledVersion.text = item.installedVersionName?.let { root.context.getString(R.string.installed_version_pattern, it) }
			textInstalledVersion.isVisible = item.installedVersionName != null
			buttonPrimary.setOnClickListener { onInstall(item) }
		}

		private fun getFallbackIcon(type: org.skepsun.kototoro.extensions.repo.ExternalExtensionType): Int = when (type) {
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.MIHON -> R.drawable.ic_source_mihon
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.ANIYOMI -> R.drawable.ic_source_aniyomi
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.IREADER -> R.drawable.ic_source_mihon // Fallback for now
			org.skepsun.kototoro.extensions.repo.ExternalExtensionType.JAR -> R.mipmap.ic_launcher // Fallback to app icon
		}
	}

	private companion object {
		val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AvailableExtensionListItem>() {
			override fun areItemsTheSame(oldItem: AvailableExtensionListItem, newItem: AvailableExtensionListItem): Boolean {
				return oldItem.extension.pkgName == newItem.extension.pkgName
			}

			override fun areContentsTheSame(oldItem: AvailableExtensionListItem, newItem: AvailableExtensionListItem): Boolean {
				return oldItem == newItem
			}
		}
	}
}
