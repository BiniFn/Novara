package org.skepsun.kototoro.settings.sources.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemExtensionRepositoryBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import java.text.DateFormat
import java.util.Date

class ExtensionRepositoriesAdapter(
	private val onOpenWebsite: (ExternalExtensionRepo) -> Unit,
	private val onDelete: (ExternalExtensionRepo) -> Unit,
) : ListAdapter<ExternalExtensionRepo, ExtensionRepositoriesAdapter.ViewHolder>(DIFF_CALLBACK) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemExtensionRepositoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding, onOpenWebsite, onDelete)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		private val binding: ItemExtensionRepositoryBinding,
		private val onOpenWebsite: (ExternalExtensionRepo) -> Unit,
		private val onDelete: (ExternalExtensionRepo) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ExternalExtensionRepo) = with(binding) {
			textName.text = item.displayName
			textWebsite.text = item.website
			textUrl.text = "${item.baseUrl}/index.min.json"
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
