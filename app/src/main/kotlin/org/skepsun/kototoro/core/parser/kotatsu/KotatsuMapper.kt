package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.model.ContentRating as KTContentRating
import org.koitharu.kotatsu.parsers.model.ContentType as KTContentType
import org.koitharu.kotatsu.parsers.model.Demographic as KTDemographic
import org.koitharu.kotatsu.parsers.model.Favicon as KTFavicon
import org.koitharu.kotatsu.parsers.model.Favicons as KTFavicons
import org.koitharu.kotatsu.parsers.model.Manga as KTManga
import org.koitharu.kotatsu.parsers.model.MangaChapter as KTMangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter as KTMangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities as KTMangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions as KTMangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage as KTMangaPage
import org.koitharu.kotatsu.parsers.model.MangaState as KTMangaState
import org.koitharu.kotatsu.parsers.model.SortOrder as KTSortOrder
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.Demographic
import org.skepsun.kototoro.parsers.model.Favicon
import org.skepsun.kototoro.parsers.model.Favicons
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaState
import org.skepsun.kototoro.parsers.model.SortOrder

internal fun KTContentType.toKototoro(): ContentType = when (this) {
	KTContentType.MANGA -> ContentType.MANGA
	KTContentType.MANHWA -> ContentType.MANHWA
	KTContentType.MANHUA -> ContentType.MANHUA
	KTContentType.HENTAI -> ContentType.HENTAI_MANGA
	KTContentType.COMICS -> ContentType.COMICS
	KTContentType.NOVEL -> ContentType.NOVEL
	KTContentType.ONE_SHOT -> ContentType.ONE_SHOT
	KTContentType.DOUJINSHI -> ContentType.DOUJINSHI
	KTContentType.IMAGE_SET -> ContentType.IMAGE_SET
	KTContentType.ARTIST_CG -> ContentType.ARTIST_CG
	KTContentType.GAME_CG -> ContentType.GAME_CG
	KTContentType.OTHER -> ContentType.OTHER
}

internal fun ContentType.toKotatsu(): KTContentType = when (this) {
	ContentType.MANGA -> KTContentType.MANGA
	ContentType.MANHWA -> KTContentType.MANHWA
	ContentType.MANHUA -> KTContentType.MANHUA
	ContentType.HENTAI_MANGA,
	ContentType.HENTAI_NOVEL,
	ContentType.HENTAI_VIDEO -> KTContentType.HENTAI
	ContentType.COMICS -> KTContentType.COMICS
	ContentType.NOVEL -> KTContentType.NOVEL
	ContentType.VIDEO -> KTContentType.MANGA
	ContentType.ONE_SHOT -> KTContentType.ONE_SHOT
	ContentType.DOUJINSHI -> KTContentType.DOUJINSHI
	ContentType.IMAGE_SET -> KTContentType.IMAGE_SET
	ContentType.ARTIST_CG -> KTContentType.ARTIST_CG
	ContentType.GAME_CG -> KTContentType.GAME_CG
	ContentType.OTHER -> KTContentType.OTHER
}

internal fun KTContentRating.toKototoro(): ContentRating = when (this) {
	KTContentRating.SAFE -> ContentRating.SAFE
	KTContentRating.SUGGESTIVE -> ContentRating.SUGGESTIVE
	KTContentRating.ADULT -> ContentRating.ADULT
}

internal fun ContentRating.toKotatsu(): KTContentRating = when (this) {
	ContentRating.SAFE -> KTContentRating.SAFE
	ContentRating.SUGGESTIVE -> KTContentRating.SUGGESTIVE
	ContentRating.ADULT -> KTContentRating.ADULT
}

internal fun KTSortOrder.toKototoro(): SortOrder = when (this) {
	KTSortOrder.UPDATED -> SortOrder.UPDATED
	KTSortOrder.UPDATED_ASC -> SortOrder.UPDATED_ASC
	KTSortOrder.POPULARITY -> SortOrder.POPULARITY
	KTSortOrder.POPULARITY_ASC -> SortOrder.POPULARITY_ASC
	KTSortOrder.RATING -> SortOrder.RATING
	KTSortOrder.RATING_ASC -> SortOrder.RATING_ASC
	KTSortOrder.NEWEST -> SortOrder.NEWEST
	KTSortOrder.NEWEST_ASC -> SortOrder.NEWEST_ASC
	KTSortOrder.ALPHABETICAL -> SortOrder.ALPHABETICAL
	KTSortOrder.ALPHABETICAL_DESC -> SortOrder.ALPHABETICAL_DESC
	KTSortOrder.ADDED -> SortOrder.ADDED
	KTSortOrder.ADDED_ASC -> SortOrder.ADDED_ASC
	KTSortOrder.RELEVANCE -> SortOrder.RELEVANCE
	KTSortOrder.POPULARITY_HOUR -> SortOrder.POPULARITY_HOUR
	KTSortOrder.POPULARITY_TODAY -> SortOrder.POPULARITY_TODAY
	KTSortOrder.POPULARITY_WEEK -> SortOrder.POPULARITY_WEEK
	KTSortOrder.POPULARITY_MONTH -> SortOrder.POPULARITY_MONTH
	KTSortOrder.POPULARITY_YEAR -> SortOrder.POPULARITY_YEAR
}

internal fun SortOrder.toKotatsu(): KTSortOrder = when (this) {
	SortOrder.UPDATED -> KTSortOrder.UPDATED
	SortOrder.UPDATED_ASC -> KTSortOrder.UPDATED_ASC
	SortOrder.POPULARITY -> KTSortOrder.POPULARITY
	SortOrder.POPULARITY_ASC -> KTSortOrder.POPULARITY_ASC
	SortOrder.RATING -> KTSortOrder.RATING
	SortOrder.RATING_ASC -> KTSortOrder.RATING_ASC
	SortOrder.NEWEST -> KTSortOrder.NEWEST
	SortOrder.NEWEST_ASC -> KTSortOrder.NEWEST_ASC
	SortOrder.ALPHABETICAL -> KTSortOrder.ALPHABETICAL
	SortOrder.ALPHABETICAL_DESC -> KTSortOrder.ALPHABETICAL_DESC
	SortOrder.ADDED -> KTSortOrder.ADDED
	SortOrder.ADDED_ASC -> KTSortOrder.ADDED_ASC
	SortOrder.RELEVANCE -> KTSortOrder.RELEVANCE
	SortOrder.POPULARITY_HOUR -> KTSortOrder.POPULARITY_HOUR
	SortOrder.POPULARITY_TODAY -> KTSortOrder.POPULARITY_TODAY
	SortOrder.POPULARITY_WEEK -> KTSortOrder.POPULARITY_WEEK
	SortOrder.POPULARITY_MONTH -> KTSortOrder.POPULARITY_MONTH
	SortOrder.POPULARITY_YEAR -> KTSortOrder.POPULARITY_YEAR
}

internal fun KTDemographic.toKototoro(): Demographic = when (this) {
	KTDemographic.SHOUNEN -> Demographic.SHOUNEN
	KTDemographic.SHOUJO -> Demographic.SHOUJO
	KTDemographic.SEINEN -> Demographic.SEINEN
	KTDemographic.JOSEI -> Demographic.JOSEI
	else -> Demographic.NONE
}

internal fun Demographic.toKotatsu(): KTDemographic = when (this) {
	Demographic.SHOUNEN -> KTDemographic.SHOUNEN
	Demographic.SHOUJO -> KTDemographic.SHOUJO
	Demographic.SEINEN -> KTDemographic.SEINEN
	Demographic.JOSEI -> KTDemographic.JOSEI
	Demographic.KODOMO -> KTDemographic.KODOMO
	Demographic.NONE -> KTDemographic.NONE
}

internal fun KTMangaState?.toKototoro(): MangaState? = when (this) {
	KTMangaState.ONGOING -> MangaState.ONGOING
	KTMangaState.FINISHED -> MangaState.FINISHED
	KTMangaState.ABANDONED -> MangaState.ABANDONED
	KTMangaState.PAUSED -> MangaState.PAUSED
	KTMangaState.UPCOMING -> MangaState.UPCOMING
	KTMangaState.RESTRICTED -> MangaState.RESTRICTED
	null -> null
}

internal fun MangaState.toKotatsu(): KTMangaState = when (this) {
	MangaState.ONGOING -> KTMangaState.ONGOING
	MangaState.FINISHED -> KTMangaState.FINISHED
	MangaState.ABANDONED -> KTMangaState.ABANDONED
	MangaState.PAUSED -> KTMangaState.PAUSED
	MangaState.UPCOMING -> KTMangaState.UPCOMING
	MangaState.RESTRICTED -> KTMangaState.RESTRICTED
}

internal fun org.koitharu.kotatsu.parsers.model.MangaTag.toKototoro(source: MangaSource): org.skepsun.kototoro.parsers.model.MangaTag =
	org.skepsun.kototoro.parsers.model.MangaTag(title, key, source)

internal fun KTFavicon.toKototoro(): Favicon = Favicon(url = url, size = size, rel = null)

internal fun KTFavicons.toKototoro(): Favicons = Favicons(favicons = map { it.toKototoro() }, referer = referer)

internal fun KTManga.toKototoro(source: MangaSource): Manga = Manga(
	id = id,
	title = title,
	altTitles = altTitles,
	url = url,
	publicUrl = publicUrl,
	rating = rating,
	contentRating = contentRating?.toKototoro(),
	coverUrl = coverUrl,
	tags = tags.mapTo(mutableSetOf()) { it.toKototoro(source) },
	state = state.toKototoro(),
	authors = authors,
	largeCoverUrl = largeCoverUrl,
	description = description,
	chapters = chapters?.map { it.toKototoro(source) },
	source = source,
)

internal fun KTMangaChapter.toKototoro(source: MangaSource): MangaChapter = MangaChapter(
	id = id,
	title = title,
	number = number,
	volume = volume,
	url = url,
	scanlator = scanlator,
	uploadDate = uploadDate,
	branch = branch,
	source = source,
)

internal fun KTMangaPage.toKototoro(source: MangaSource): MangaPage = MangaPage(
	id = id,
	url = url,
	preview = preview,
	headers = null,
	source = source,
)

internal fun KTMangaListFilterOptions.toKototoro(source: MangaSource): MangaListFilterOptions = MangaListFilterOptions(
	availableTags = availableTags.mapTo(mutableSetOf()) { it.toKototoro(source) },
	availableStates = availableStates.mapNotNullTo(mutableSetOf()) { it.toKototoro() },
	availableContentRating = availableContentRating.mapTo(mutableSetOf()) { it.toKototoro() },
	availableContentTypes = availableContentTypes.mapTo(mutableSetOf()) { it.toKototoro() },
	availableDemographics = availableDemographics.mapTo(mutableSetOf()) { it.toKototoro() },
	availableLocales = availableLocales,
)

internal fun KTMangaListFilter.toKototoro(source: MangaSource): MangaListFilter = MangaListFilter(
	query = query,
	tags = tags.mapTo(mutableSetOf()) { it.toKototoro(source) },
	tagsExclude = tagsExclude.mapTo(mutableSetOf()) { it.toKototoro(source) },
	locale = locale,
	originalLocale = originalLocale,
	states = states.mapNotNullTo(mutableSetOf()) { it.toKototoro() },
	contentRating = contentRating.mapTo(mutableSetOf()) { it.toKototoro() },
	types = types.mapTo(mutableSetOf()) { it.toKototoro() },
	demographics = demographics.mapTo(mutableSetOf()) { it.toKototoro() },
	year = year,
	yearFrom = yearFrom,
	yearTo = yearTo,
	author = author,
)

internal fun KTMangaListFilterCapabilities.toKototoro(): MangaListFilterCapabilities =
	MangaListFilterCapabilities(
		isMultipleTagsSupported = isMultipleTagsSupported,
		isTagsExclusionSupported = isTagsExclusionSupported,
		isSearchSupported = isSearchSupported,
		isSearchWithFiltersSupported = isSearchWithFiltersSupported,
		isYearSupported = isYearSupported,
		isYearRangeSupported = isYearRangeSupported,
		isOriginalLocaleSupported = isOriginalLocaleSupported,
		isAuthorSearchSupported = isAuthorSearchSupported,
	)

internal fun Manga.toKotatsu(source: KotatsuParserSource): KTManga = KTManga(
	id = id,
	title = title,
	altTitles = altTitles,
	url = url,
	publicUrl = publicUrl,
	rating = rating,
	contentRating = contentRating?.toKotatsu(),
	coverUrl = coverUrl,
	tags = tags.mapTo(mutableSetOf()) { org.koitharu.kotatsu.parsers.model.MangaTag(it.title, it.key, source.delegate) },
	state = state?.toKotatsu(),
	authors = authors,
	largeCoverUrl = largeCoverUrl,
	description = description,
	chapters = chapters?.map { it.toKotatsu(source) },
	source = source.delegate,
)

internal fun MangaChapter.toKotatsu(source: KotatsuParserSource): KTMangaChapter = KTMangaChapter(
	id = id,
	title = title,
	number = number,
	volume = volume,
	url = url,
	scanlator = scanlator,
	uploadDate = uploadDate,
	branch = branch,
	source = source.delegate,
)

internal fun MangaPage.toKotatsu(source: KotatsuParserSource): KTMangaPage = KTMangaPage(
	id = id,
	url = url,
	preview = preview,
	source = source.delegate,
)

internal fun MangaListFilter.toKotatsu(source: KotatsuParserSource): KTMangaListFilter = KTMangaListFilter(
	query = query,
	tags = tags.mapTo(mutableSetOf()) { org.koitharu.kotatsu.parsers.model.MangaTag(it.title, it.key, source.delegate) },
	tagsExclude = tagsExclude.mapTo(mutableSetOf()) { org.koitharu.kotatsu.parsers.model.MangaTag(it.title, it.key, source.delegate) },
	locale = locale,
	originalLocale = originalLocale,
	states = states.mapTo(mutableSetOf<org.koitharu.kotatsu.parsers.model.MangaState>()) { it.toKotatsu() },
	contentRating = contentRating.mapTo(mutableSetOf()) { it.toKotatsu() },
	types = types.mapTo(mutableSetOf()) { it.toKotatsu() },
	demographics = demographics.mapTo(mutableSetOf()) { it.toKotatsu() },
	year = year,
	yearFrom = yearFrom,
	yearTo = yearTo,
	author = author,
)
