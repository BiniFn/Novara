package org.skepsun.kototoro.search.domain

import androidx.annotation.StringRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.explore.ui.model.SourceTag

val ALL_SOURCE_TYPES: Set<SourceType> = setOf(
	SourceType.NATIVE,
	SourceType.JSON_LEGADO,
	SourceType.JSON_TVBOX,
	SourceType.MIHON,
	SourceType.ANIYOMI,
)

data class SourceTypeOption(
	val type: SourceType,
	@StringRes val titleRes: Int,
)

val SOURCE_TYPE_OPTIONS: List<SourceTypeOption> = listOf(
	SourceTypeOption(SourceType.NATIVE, R.string.source_type_native),
	SourceTypeOption(SourceType.MIHON, R.string.source_type_mihon),
	SourceTypeOption(SourceType.ANIYOMI, R.string.source_type_aniyomi),
	SourceTypeOption(SourceType.JSON_LEGADO, R.string.source_type_legado),
	SourceTypeOption(SourceType.JSON_TVBOX, R.string.source_type_tvbox),
)

fun sourceTypesFromTags(tags: Set<SourceTag>): Set<SourceType> {
	if (tags.isEmpty()) {
		return ALL_SOURCE_TYPES
	}
	val result = mutableSetOf<SourceType>()
	tags.forEach { tag ->
		when (tag) {
			SourceTag.BUILTIN -> result.add(SourceType.NATIVE)
			SourceTag.MIHON -> result.add(SourceType.MIHON)
			SourceTag.ANIYOMI -> result.add(SourceType.ANIYOMI)
			SourceTag.LEGADO -> result.add(SourceType.JSON_LEGADO)
			SourceTag.TVBOX -> result.add(SourceType.JSON_TVBOX)
		}
	}
	return if (result.isEmpty()) ALL_SOURCE_TYPES else result
}

fun sourceTypesFromNames(names: Collection<String>?): Set<SourceType>? {
	if (names.isNullOrEmpty()) return null
	val types = names.mapNotNull { name ->
		runCatching { SourceType.valueOf(name) }.getOrNull()
	}.toSet()
	val filtered = types.intersect(ALL_SOURCE_TYPES)
	return filtered.ifEmpty { null }
}

fun sourceTypesToNames(types: Set<SourceType>): ArrayList<String> {
	return ArrayList(types.map { it.name })
}
