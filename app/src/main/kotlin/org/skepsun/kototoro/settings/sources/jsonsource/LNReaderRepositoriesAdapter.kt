package org.skepsun.kototoro.settings.sources.jsonsource

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.databinding.ItemLnreaderRepositoryBinding

class LNReaderRepositoriesAdapter(
	private val onDeleteClick: (String) -> Unit,
) : ListAdapter<String, LNReaderRepositoriesAdapter.ViewHolder>(ItemCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemLnreaderRepositoryBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false,
		)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	inner class ViewHolder(
		private val binding: ItemLnreaderRepositoryBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		init {
			binding.buttonDelete.setOnClickListener {
				val position = bindingAdapterPosition
				if (position != RecyclerView.NO_POSITION) {
					onDeleteClick(getItem(position))
				}
			}
		}

		fun bind(url: String) {
			binding.textUrl.text = url
		}
	}

	private object ItemCallback : DiffUtil.ItemCallback<String>() {
		override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
		override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
	}
}
