package org.skepsun.kototoro.reader.ui.pager

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.parcelize.RawValue
import org.skepsun.kototoro.core.model.parcelable.MangaSourceParceler
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource

@Parcelize
@TypeParceler<MangaSource, MangaSourceParceler>
data class ReaderPage(
	val id: Long,
	val url: String,
	val preview: String?,
	val headers: @RawValue Map<String, String>?,
	val chapterId: Long,
	val index: Int,
	val source: MangaSource,
) : Parcelable {

	constructor(page: MangaPage, index: Int, chapterId: Long) : this(
		id = page.id,
		url = page.url,
		preview = page.preview,
		headers = page.headers,
		chapterId = chapterId,
		index = index,
		source = page.source,
	)

	fun toMangaPage() = MangaPage(
		id = id,
		url = url,
		preview = preview,
		headers = headers,
		source = source,
	)
}
