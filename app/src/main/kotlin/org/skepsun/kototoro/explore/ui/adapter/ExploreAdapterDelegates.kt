package org.skepsun.kototoro.explore.ui.adapter

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.core.util.ext.recyclerView
import org.skepsun.kototoro.core.util.ext.setProgressIcon
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemExploreButtonsBinding
import org.skepsun.kototoro.databinding.ItemExploreSourceGridBinding
import org.skepsun.kototoro.databinding.ItemExploreSourceListBinding
import org.skepsun.kototoro.databinding.ItemRecommendationBinding
import org.skepsun.kototoro.databinding.ItemRecommendationMangaBinding
import org.skepsun.kototoro.explore.ui.model.ExploreButtons
import org.skepsun.kototoro.explore.ui.model.MangaSourceItem
import org.skepsun.kototoro.explore.ui.model.RecommendationsItem
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.MangaCompactListModel
import org.skepsun.kototoro.parsers.model.Manga

fun exploreButtonsAD(
	clickListener: View.OnClickListener,
) = adapterDelegateViewBinding<ExploreButtons, ListModel, ItemExploreButtonsBinding>(
	{ layoutInflater, parent -> ItemExploreButtonsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.buttonBookmarks.setOnClickListener(clickListener)
	binding.buttonDownloads.setOnClickListener(clickListener)
	binding.buttonLocal.setOnClickListener(clickListener)
	binding.buttonRandom.setOnClickListener(clickListener)

	bind {
		if (item.isRandomLoading) {
			binding.buttonRandom.setProgressIcon()
		} else {
			binding.buttonRandom.setIconResource(R.drawable.ic_dice)
		}
		binding.buttonRandom.isClickable = !item.isRandomLoading
	}
}

fun exploreRecommendationItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<RecommendationsItem, ListModel, ItemRecommendationBinding>(
	{ layoutInflater, parent -> ItemRecommendationBinding.inflate(layoutInflater, parent, false) },
) {

	val adapter = BaseListAdapter<MangaCompactListModel>()
		.addDelegate(ListItemType.MANGA_LIST, recommendationMangaItemAD(itemClickListener))
	binding.pager.adapter = adapter
	binding.pager.recyclerView?.isNestedScrollingEnabled = false
	binding.dots.bindToViewPager(binding.pager)

	bind {
		adapter.items = item.manga
	}
}

fun recommendationMangaItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<MangaCompactListModel, MangaCompactListModel, ItemRecommendationMangaBinding>(
	{ layoutInflater, parent -> ItemRecommendationMangaBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		itemClickListener.onItemClick(item.manga, v)
	}
	bind {
		binding.textViewTitle.text = item.manga.title
		binding.textViewSubtitle.textAndVisible = item.subtitle
		binding.imageViewCover.setImageAsync(item.manga.coverUrl, item.manga.source)
	}
}


fun exploreSourceListItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceListBinding>(
	{ layoutInflater, parent ->
		ItemExploreSourceListBinding.inflate(
			layoutInflater,
			parent,
			false,
		)
	},
	on = { item, _, _ -> item is MangaSourceItem && !item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	val sourceTypeIdentifier = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier()

	bind {
		binding.textViewTitle.text = item.source.getTitle(context)
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		binding.textViewSubtitle.text = item.source.getSummary(context)
		binding.imageViewIcon.setImageAsync(item.source)
		
		// Show source type chip for JSON sources
		val sourceId = item.source.mangaSource.name
		if (sourceTypeIdentifier.isJsonSource(sourceId)) {
			binding.chipSourceType.visibility = View.VISIBLE
			val sourceType = sourceTypeIdentifier.getSourceType(sourceId)
			binding.chipSourceType.text = when (sourceType) {
				org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO -> "JSON"
				org.skepsun.kototoro.core.jsonsource.SourceType.JSON_TVBOX -> "TVBox"
				else -> "JSON"
			}
			// Set chip color for JSON sources (orange tint)
			binding.chipSourceType.setChipBackgroundColorResource(R.color.orange_100)
			binding.chipSourceType.setTextColor(ContextCompat.getColor(context, R.color.orange_900))
		} else {
			binding.chipSourceType.visibility = View.GONE
		}
	}
}

fun exploreSourceGridItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceGridBinding>(
	{ layoutInflater, parent ->
		ItemExploreSourceGridBinding.inflate(
			layoutInflater,
			parent,
			false,
		)
	},
	on = { item, _, _ -> item is MangaSourceItem && item.isGrid },
) {

	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	val sourceTypeIdentifier = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier()

	bind {
		val title = item.source.getTitle(context)
		val summary = item.source.getSummary(context)
		itemView.setTooltipCompat(
			buildSpannedString {
				bold {
					append(title)
				}
				if (!summary.isNullOrEmpty()) {
					appendLine()
					append(summary)
				}
			},
		)
		binding.textViewTitle.text = title
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		binding.imageViewIcon.setImageAsync(item.source)
		
		// Show source type chip for JSON sources
		val sourceId = item.source.mangaSource.name
		if (sourceTypeIdentifier.isJsonSource(sourceId)) {
			binding.chipSourceType.visibility = View.VISIBLE
			val sourceType = sourceTypeIdentifier.getSourceType(sourceId)
			binding.chipSourceType.text = when (sourceType) {
				org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO -> "JSON"
				org.skepsun.kototoro.core.jsonsource.SourceType.JSON_TVBOX -> "TVBox"
				else -> "JSON"
			}
			// Set chip color for JSON sources (orange tint)
			binding.chipSourceType.setChipBackgroundColorResource(R.color.orange_100)
			binding.chipSourceType.setTextColor(ContextCompat.getColor(context, R.color.orange_900))
		} else {
			binding.chipSourceType.visibility = View.GONE
		}
	}
}
