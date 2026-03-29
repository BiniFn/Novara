package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemExtensionRepositoryBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension
import java.text.DateFormat
import java.util.Date

class ExtensionRepositoriesAdapter(
	private val onOpenWebsite: (ExternalExtensionRepo) -> Unit,
	private val onDelete: (ExternalExtensionRepo) -> Unit,
	private val onUpdate: (ExternalExtensionRepo) -> Unit,
) : ListAdapter<ExternalExtensionRepo, ExtensionRepositoriesAdapter.ViewHolder>(DIFF_CALLBACK) {

	private var updates: Map<String, RepoAvailableExtension> = emptyMap()

	fun setUpdates(updates: Map<String, RepoAvailableExtension>) {
		this.updates = updates
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemExtensionRepositoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding, onOpenWebsite, onDelete, onUpdate)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = getItem(position)
		holder.bind(item, updates[item.baseUrl])
	}

	class ViewHolder(
		private val binding: ItemExtensionRepositoryBinding,
		private val onOpenWebsite: (ExternalExtensionRepo) -> Unit,
		private val onDelete: (ExternalExtensionRepo) -> Unit,
		private val onUpdate: (ExternalExtensionRepo) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExternalExtensionRepo, updateInfo: RepoAvailableExtension?) = with(binding) {
			textName.text = item.displayName
			textWebsite.text = item.website
			textUrl.text = "${item.baseUrl}/index.min.json"
			textVersion.text = if (item.version != null) {
				"v${item.version}" + if (updateInfo != null) " (Update: v${updateInfo.versionName})" else ""
			} else {
				""
			}
			textVersion.visibility = if (textVersion.text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
			buttonUpdate.visibility = if (updateInfo != null) android.view.View.VISIBLE else android.view.View.GONE
			textFingerprint.text = item.signingKeyFingerprint.formatExtensionFingerprint()
			textStatus.text = if (item.lastError.isNullOrBlank()) {
				root.context.getString(
					R.string.extension_repository_last_refreshed_message,
					DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.lastSuccessAt)),
				)
			} else {
				item.lastError
			}
			buttonOpen.setOnClickListener { onOpenWebsite(item) }
			buttonDelete.setOnClickListener { onDelete(item) }
			buttonUpdate.setOnClickListener { onUpdate(item) }
		}
	}

	private companion object {
		val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExternalExtensionRepo>() {
			override fun areItemsTheSame(oldItem: ExternalExtensionRepo, newItem: ExternalExtensionRepo): Boolean {
				return oldItem.type == newItem.type && oldItem.baseUrl == newItem.baseUrl
			}

			override fun areContentsTheSame(oldItem: ExternalExtensionRepo, newItem: ExternalExtensionRepo): Boolean {
				return oldItem == newItem
			}
		}
	}
}
