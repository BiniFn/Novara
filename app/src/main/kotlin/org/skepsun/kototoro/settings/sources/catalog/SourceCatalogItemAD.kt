package org.skepsun.kototoro.settings.sources.catalog

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getSummary
import org.skepsun.kototoro.core.model.getTitle
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
		binding.textViewTitle.text = item.source.getTitle(context)
		binding.textViewDescription.text = item.source.getSummary(context)
		binding.textViewDescription.drawableStart = if (item.source.isBroken) {
			ContextCompat.getDrawable(context, R.drawable.ic_off_small)
		} else {
			null
		}
		FaviconDrawable(context, R.style.FaviconDrawable_Small, item.source.name)
		binding.imageViewIcon.setImageAsync(item.source)
		
		// Show source type chip for JSON sources
		val sourceId = item.source.name
		if (sourceTypeIdentifier.isJsonSource(sourceId)) {
			binding.chipSourceType.visibility = android.view.View.VISIBLE
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
			binding.chipSourceType.visibility = android.view.View.GONE
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
