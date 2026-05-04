package org.skepsun.kototoro.settings.userdata.storage

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.core.widget.TextViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlinx.coroutines.flow.FlowCollector
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.widgets.SegmentedBarView
import org.skepsun.kototoro.core.util.FileSize
import org.skepsun.kototoro.core.util.KototoroColors
import org.skepsun.kototoro.databinding.PreferenceMemoryUsageBinding

class StorageUsagePreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs), FlowCollector<StorageUsage?> {

	private val labelPattern = context.getString(R.string.memory_usage_pattern)
	private var usage: StorageUsage? = null

	init {
		layoutResource = R.layout.preference_memory_usage
		isSelectable = false
		isPersistent = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		val binding = PreferenceMemoryUsageBinding.bind(holder.itemView)
		val savedContent = usage.sumOf(
			StorageUsageCategory.LOCAL_MANGA,
			StorageUsageCategory.LOCAL_NOVELS,
			StorageUsageCategory.LOCAL_VIDEOS,
		)
		val pagesCache = usage.find(StorageUsageCategory.PAGES_CACHE)
		val aiModels = usage.find(StorageUsageCategory.AI_MODELS)
		val available = usage.find(StorageUsageCategory.AVAILABLE)
		val otherCache = usage.aggregateCacheExcluding(
			StorageUsageCategory.PAGES_CACHE,
			StorageUsageCategory.AVAILABLE,
		)
		val storageSegment = SegmentedBarView.Segment(
			savedContent?.percent ?: 0f,
			KototoroColors.segmentColorRandom(context, 210), // Blue-ish
		)
		val pagesSegment = SegmentedBarView.Segment(
			pagesCache?.percent ?: 0f,
			KototoroColors.segmentColorRandom(context, 120), // Green
		)
		val aiModelsSegment = SegmentedBarView.Segment(
			aiModels?.percent ?: 0f,
			KototoroColors.segmentColorRandom(context, 300), // Magenta/Purple
		)
		val otherSegment = SegmentedBarView.Segment(
			otherCache?.percent ?: 0f,
			KototoroColors.segmentColorRandom(context, 30), // Orange/Brown 
		)

		with(binding) {
			bar.animateSegments(listOf(storageSegment, aiModelsSegment, pagesSegment, otherSegment).filter { it.percent > 0f })
			labelStorage.text = formatLabel(savedContent, R.string.saved_manga)
			labelAiModels.text = formatLabel(aiModels, R.string.ai_local_models)
			labelPagesCache.text = formatLabel(pagesCache, R.string.pages_cache)
			labelOtherCache.text = formatLabel(otherCache, R.string.other_cache)
			labelAvailable.text = formatLabel(available, R.string.available, R.string.available)

			TextViewCompat.setCompoundDrawableTintList(labelStorage, ColorStateList.valueOf(storageSegment.color))
			TextViewCompat.setCompoundDrawableTintList(labelAiModels, ColorStateList.valueOf(aiModelsSegment.color))
			TextViewCompat.setCompoundDrawableTintList(labelPagesCache, ColorStateList.valueOf(pagesSegment.color))
			TextViewCompat.setCompoundDrawableTintList(labelOtherCache, ColorStateList.valueOf(otherSegment.color))
		}
	}

	override suspend fun emit(value: StorageUsage?) {
		usage = value
		notifyChanged()
	}

	private fun formatLabel(
		item: StorageUsage.Item?,
		@StringRes labelResId: Int,
		@StringRes emptyResId: Int = R.string.computing_,
	): String {
		return if (item != null) {
			labelPattern.format(
				FileSize.BYTES.format(context, item.bytes),
				context.getString(labelResId),
			)
		} else {
			context.getString(emptyResId)
		}
	}

	private fun StorageUsage?.find(category: StorageUsageCategory): StorageUsage.Item? {
		return this?.find(category)
	}

	private fun StorageUsage?.sumOf(vararg categories: StorageUsageCategory): StorageUsage.Item? {
		val items = this?.items.orEmpty()
		if (items.isEmpty()) return null
		val matched = items.filter { it.category in categories.toSet() }
		if (matched.isEmpty()) return null
		return StorageUsage.Item(
			category = categories.first(),
			bytes = matched.sumOf { it.bytes },
			percent = matched.sumOf { it.percent.toDouble() }.toFloat(),
		)
	}

	private fun StorageUsage?.aggregateCacheExcluding(vararg categories: StorageUsageCategory): StorageUsage.Item? {
		val excluded = categories.toSet()
		val matched = this?.items.orEmpty().filter {
			it.category.name.endsWith("_CACHE") && it.category !in excluded
		}
		if (matched.isEmpty()) return null
		return StorageUsage.Item(
			category = StorageUsageCategory.OTHER_CACHE,
			bytes = matched.sumOf { it.bytes },
			percent = matched.sumOf { it.percent.toDouble() }.toFloat(),
		)
	}
}
