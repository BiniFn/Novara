package org.skepsun.kototoro.settings.sources.catalog

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource
import org.skepsun.kototoro.core.ui.image.FaviconDrawable
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.core.util.ext.getThemeDimensionPixelOffset
import org.skepsun.kototoro.core.util.ext.setTextAndVisible
import org.skepsun.kototoro.databinding.ItemEmptyHintBinding
import org.skepsun.kototoro.databinding.ItemSourceCatalogBinding
import org.skepsun.kototoro.list.ui.model.ListModel
import androidx.appcompat.R as appcompatR

fun sourceCatalogItemSourceAD(
	listener: OnListItemClickListener<SourceCatalogItem.Source>
) = adapterDelegateViewBinding<SourceCatalogItem.Source, ListModel, ItemSourceCatalogBinding>(
	{ layoutInflater, parent ->
		ItemSourceCatalogBinding.inflate(layoutInflater, parent, false)
	},
) {

	binding.imageViewAdd.setOnClickListener { v ->
		listener.onItemLongClick(item, v)
	}
	binding.root.setOnClickListener { v ->
		listener.onItemClick(item, v)
	}
	val basePadding = context.getThemeDimensionPixelOffset(
		appcompatR.attr.listPreferredItemPaddingEnd,
		binding.root.paddingStart,
	)
	binding.root.updatePaddingRelative(
		end = (basePadding - context.resources.getDimensionPixelOffset(R.dimen.margin_small)).coerceAtLeast(0),
	)

	val sourceTypeIdentifier = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier()

	bind {
		val unwrapped = item.source.unwrap()
		val isBroken = when (unwrapped) {
			is org.skepsun.kototoro.parsers.model.MangaParserSource -> unwrapped.isBroken
			is KotatsuParserSource -> unwrapped.isBroken
			else -> false
		}

		binding.textViewTitle.text = item.source.getTitle(context)
		binding.textViewDescription.text = item.source.getSummary(context)
		binding.textViewDescription.drawableStart = if (isBroken) {
			ContextCompat.getDrawable(context, R.drawable.ic_off_small)
		} else {
			null
		}
		binding.imageViewIcon.setImageAsync(item.source)
		
		// JSON badges removed as per user request
		binding.chipSourceType.visibility = android.view.View.GONE
		val sourceId = item.source.name
		if (sourceTypeIdentifier.isJsonSource(sourceId)) {
			// 显示人类可读名称
			if (item.source is org.skepsun.kototoro.core.jsonsource.JsonMangaSource) {
				binding.textViewTitle.text = (item.source as org.skepsun.kototoro.core.jsonsource.JsonMangaSource).displayName.ifBlank { item.source.name }
			}
		}
	}
}

fun sourceCatalogItemHintAD() = adapterDelegateViewBinding<SourceCatalogItem.Hint, ListModel, ItemEmptyHintBinding>(
	{ inflater, parent -> ItemEmptyHintBinding.inflate(inflater, parent, false) },
) {

	binding.buttonRetry.isVisible = false

	bind {
		binding.icon.setImageAsync(item.icon)
		binding.textPrimary.setText(item.title)
		binding.textSecondary.setTextAndVisible(item.text)
	}
}
