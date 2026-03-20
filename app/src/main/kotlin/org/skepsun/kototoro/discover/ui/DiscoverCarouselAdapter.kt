package org.skepsun.kototoro.discover.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.databinding.ItemDiscoverCarouselBinding
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.skepsun.kototoro.list.ui.adapter.ContentListAdapter
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.list.ui.size.StaticItemSizeResolver

class DiscoverCarouselAdapter(
	private val settings: AppSettings,
	private val contentListener: ContentListListener,
	private val onMoreClick: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCategory) -> Unit,
) : RecyclerView.Adapter<DiscoverCarouselAdapter.CarouselViewHolder>() {

	private var items: List<DiscoverCarouselRow> = emptyList()

	fun submitList(newItems: List<DiscoverCarouselRow>) {
		this.items = newItems
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
		val binding = ItemDiscoverCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return CarouselViewHolder(binding)
	}

	override fun getItemCount(): Int = items.size

	override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
		holder.bind(items[position])
	}

	inner class CarouselViewHolder(private val binding: ItemDiscoverCarouselBinding) : RecyclerView.ViewHolder(binding.root) {
		private val horizontalAdapter = ContentListAdapter(
			listener = contentListener,
			sizeResolver = org.skepsun.kototoro.list.ui.size.StaticItemSizeResolver(
				binding.root.resources.getDimensionPixelSize(org.skepsun.kototoro.R.dimen.preferred_grid_width)
			)
		)

		init {
			binding.recyclerViewCarousel.layoutManager = LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
			binding.recyclerViewCarousel.adapter = horizontalAdapter
			binding.recyclerViewCarousel.setHasFixedSize(true)
			binding.recyclerViewCarousel.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
				private var startX = 0f
				private var startY = 0f
				override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
					when (e.action) {
						android.view.MotionEvent.ACTION_DOWN -> {
							startX = e.x
							startY = e.y
							rv.parent.requestDisallowInterceptTouchEvent(true)
						}
						android.view.MotionEvent.ACTION_MOVE -> {
							val dx = kotlin.math.abs(e.x - startX)
							val dy = kotlin.math.abs(e.y - startY)
							if (dy > dx) rv.parent.requestDisallowInterceptTouchEvent(false)
						}
						android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
							rv.parent.requestDisallowInterceptTouchEvent(false)
						}
					}
					return false
				}
				override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
				override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
			})
		}

		fun bind(row: DiscoverCarouselRow) {
			binding.textViewCategoryTitle.setText(row.category.nameResId)
			binding.buttonMore.setOnClickListener { onMoreClick(row.category) }
			MainScope().launch {
				horizontalAdapter.emit(row.items)
			}
		}
	}
}
