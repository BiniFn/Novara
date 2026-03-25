package org.skepsun.kototoro.core.model

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.external.ExternalContentSource
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.toLocale
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParsersProvider
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.ContentParserSource
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.util.splitTwoParts
import org.json.JSONObject
import java.util.Locale

data object LocalMangaSource : ContentSource {
	override val name = "LOCAL"
}

val ContentSource.isLocal: Boolean
	get() = this == LocalMangaSource || this == LocalNovelSource

data object LocalNovelSource : ContentSource {
	override val name = "LOCAL_NOVEL"
}

data object UnknownContentSource : ContentSource {
	override val name = "UNKNOWN"
}

data object TestContentSource : ContentSource {
	override val name = "TEST"
}

fun ContentSource(name: String?): ContentSource {
	when (name ?: return UnknownContentSource) {
	UnknownContentSource.name -> return UnknownContentSource
	LocalMangaSource.name -> return LocalMangaSource
	LocalNovelSource.name -> return LocalNovelSource
	TestContentSource.name -> return TestContentSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownContentSource
		return ExternalContentSource(packageName = parts.first, authority = parts.second)
	}
	// Check if it's a JSON source (starts with JSON_ prefix)
	if (name.startsWith("JSON_")) {
		android.util.Log.d("ContentSource", "Detected JSON source name: $name, returning stable ContentSource")
		return AnonymousContentSource(name)
	}
	// Check if it's a Tracking source (starts with TRACKING_ prefix)
	if (name.startsWith("TRACKING_")) {
		return AnonymousContentSource(name)
	}
	// Check if it's a Mihon source (starts with MIHON_ prefix)
	if (name.startsWith("MIHON_")) {
		android.util.Log.d("ContentSource", "Detected Mihon source name: $name, returning stable ContentSource")
		return AnonymousContentSource(name)
	}
	// Check if it's an Aniyomi source (starts with ANIYOMI_ prefix)
	if (name.startsWith("ANIYOMI_")) {
		android.util.Log.d("ContentSource", "Detected Aniyomi source name: $name, returning stable ContentSource")
		return AnonymousContentSource(name)
	}
	// Check if it's an IReader source (starts with IREADER_ prefix)
	if (name.startsWith("IREADER_")) {
		android.util.Log.d("ContentSource", "Detected IReader source name: $name, returning stable ContentSource")
		return AnonymousContentSource(name)
	}
	KotatsuParsersProvider.findByName(name)?.let { return it }
	ContentParserSource.entries.forEach {
		if (it.name == name) return it
	}
	return UnknownContentSource
}

fun Collection<String>.toContentSources() = map(::ContentSource)

fun ContentSource.isNsfw(): Boolean = when (this) {
	is ContentSourceInfo -> mangaSource.isNsfw()
	is ContentParserSource -> contentType in setOf(
		ContentType.HENTAI_MANGA,
		ContentType.HENTAI_NOVEL,
		ContentType.HENTAI_VIDEO,
	)
	is KotatsuParserSource -> contentType in setOf(
		ContentType.HENTAI_MANGA,
		ContentType.HENTAI_NOVEL,
		ContentType.HENTAI_VIDEO,
	)
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> isNsfw
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> isNsfw
	is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> isNsfw
	else -> false
}

@get:StringRes
val ContentType.titleResId
	get() = when (this) {
		ContentType.MANGA -> R.string.content_type_manga
		ContentType.HENTAI_MANGA -> R.string.content_type_hentai_manga
		ContentType.HENTAI_NOVEL -> R.string.content_type_hentai_novel
		ContentType.HENTAI_VIDEO -> R.string.content_type_hentai_video
		ContentType.COMICS -> R.string.content_type_comics
		ContentType.VIDEO -> R.string.content_type_video
		ContentType.OTHER -> R.string.content_type_other
		ContentType.MANHWA -> R.string.content_type_manhwa
		ContentType.MANHUA -> R.string.content_type_manhua
		ContentType.NOVEL -> R.string.content_type_novel
		ContentType.ONE_SHOT -> R.string.content_type_one_shot
		ContentType.DOUJINSHI -> R.string.content_type_doujinshi
		ContentType.IMAGE_SET -> R.string.content_type_image_set
		ContentType.ARTIST_CG -> R.string.content_type_artist_cg
		ContentType.GAME_CG -> R.string.content_type_game_cg
	}

fun ContentType.getEnableSourceTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.enable_source_novel
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.enable_source_video
	else -> R.string.enable_source_manga
}

fun ContentType.getUnsupportedSourceTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.unsupported_novel_source
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.unsupported_video_source
	else -> R.string.unsupported_source
}

fun ContentType.getDomainTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.domain_novel
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.domain_video
	else -> R.string.domain_manga
}

fun ContentType.getSaveTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.save_manga_novel
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.save_manga_video
	else -> R.string.save_manga_manga
}

fun ContentType.getWholeWorkOptionResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.download_option_whole_manga_novel
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.download_option_whole_manga_video
	else -> R.string.download_option_whole_manga_manga
}

fun ContentType.getRecommendationTermResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.recommendation_novel
	ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.recommendation_video
	else -> R.string.recommendation_manga
}

tailrec fun ContentSource.unwrap(): ContentSource = if (this is ContentSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun ContentSource.getLocale(): Locale? = (unwrap() as? ContentParserSource)?.locale?.toLocaleOrNull()
	?: (unwrap() as? KotatsuParserSource)?.locale?.toLocaleOrNull()
	?: (unwrap() as? org.skepsun.kototoro.ireader.model.IReaderMangaSource)?.let {
		mapIReaderLangToLocale(it.language)?.toLocaleOrNull()
	}

fun ContentSource.getContentType(): ContentType = when (val source = unwrap()) {
	is ContentParserSource -> source.contentType
	is KotatsuParserSource -> source.contentType
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> if (source.isNsfw) ContentType.HENTAI_MANGA else ContentType.MANGA
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> if (source.isNsfw) ContentType.HENTAI_VIDEO else ContentType.VIDEO
	is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> if (source.isNsfw) ContentType.HENTAI_NOVEL else ContentType.NOVEL
	is org.skepsun.kototoro.core.jsonsource.JsonContentSource -> {
		when (source.entity.type) {
			JsonSourceType.TVBOX -> ContentType.VIDEO
			JsonSourceType.JS -> ContentType.MANGA
			JsonSourceType.LNREADER -> ContentType.NOVEL
			JsonSourceType.LEGADO -> try {
				val jsonObj = JSONObject(source.entity.config)
				if (jsonObj.optInt("bookSourceType", 0) == 2) ContentType.MANGA else ContentType.NOVEL
			} catch (e: Exception) {
				ContentType.NOVEL
			}
		}
	}
	else -> {
		// Fallback for serialized sources that lost their type info (e.g., through Parcelable)
		// Detect by name prefix
		val sourceName = source.name
		when {
			sourceName.startsWith("ANIYOMI_") -> ContentType.VIDEO  // Aniyomi sources are always video
			sourceName.startsWith("JSON_TVBOX_") -> ContentType.VIDEO
			sourceName.startsWith("JSON_LEGADO_M_") -> ContentType.MANGA
			sourceName.startsWith("JSON_LEGADO_") -> {
				// We can't know for sure here without the entity, but let's default to novel
				// as Legado is primarily a novel engine.
				ContentType.NOVEL
			}
			sourceName.startsWith("JSON_LNREADER_") -> ContentType.NOVEL
			sourceName.startsWith("JSON_JS_") -> ContentType.MANGA
			sourceName.startsWith("TRACKING_") -> ContentType.OTHER
			sourceName.startsWith("IREADER_") -> ContentType.NOVEL
			else -> ContentType.MANGA
		}
	}
}

fun ContentSource.getSummary(context: Context, contentType: ContentType? = null): String? = when (val source = unwrap()) {
	is ContentParserSource,
	is KotatsuParserSource,
	is org.skepsun.kototoro.mihon.model.MihonMangaSource,
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource,
	is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		val lang = when (source) {
			is ContentParserSource -> source.locale.toLocale()
			is KotatsuParserSource -> source.locale.toLocale()
			is org.skepsun.kototoro.mihon.model.MihonMangaSource -> source.language.toLocale()
			is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> source.language.toLocale()
			is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> source.language.toLocale()
			else -> Locale.getDefault()
		}
		val locale = lang.getDisplayName(context)
		val base = context.getString(R.string.source_summary_pattern, type, locale)
		appendOriginSuffix(context, base, source.getOriginLabel(context))
	}

	is ExternalContentSource -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		appendOriginSuffix(context, type, source.getOriginLabel(context))
	}
	
	is org.skepsun.kototoro.core.jsonsource.JsonContentSource -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		appendOriginSuffix(context, type, source.getOriginLabel(context))
	}

	else -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		appendOriginSuffix(context, type, source.getOriginLabel(context))
	}
}

private fun appendOriginSuffix(context: Context, base: String, originLabel: String?): String {
	if (originLabel.isNullOrBlank()) {
		return base
	}
	val currentLanguage = context.resources.configuration.locales.get(0)?.language.orEmpty()
	val (open, close) = if (currentLanguage == "zh") "（" to "）" else "(" to ")"
	return "$base$open$originLabel$close"
}

fun ContentSource.getOriginLabel(context: Context): String? = when (this) {
	is ContentSourceInfo -> mangaSource.getOriginLabel(context)
	is ContentParserSource -> "内置"
	is KotatsuParserSource -> "Kotatsu"
	is ExternalContentSource -> context.getString(R.string.external_source)
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> "Mihon"
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> "Aniyomi"
	is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> "IReader"
	is org.skepsun.kototoro.core.jsonsource.JsonContentSource -> {
		val type = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier().getSourceType(name)
		when (type) {
			org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO -> "Legado"
			org.skepsun.kototoro.core.jsonsource.SourceType.JSON_TVBOX -> "TVBox"
			org.skepsun.kototoro.core.jsonsource.SourceType.JSON_JS -> "JS"
			else -> "JSON"
		}
	}
	else -> {
		val type = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier().getSourceType(name)
		when {
			name.startsWith("TRACKING_") -> name.removePrefix("TRACKING_")
			type == org.skepsun.kototoro.core.jsonsource.SourceType.MIHON -> "Mihon"
			type == org.skepsun.kototoro.core.jsonsource.SourceType.ANIYOMI -> "Aniyomi"
			type == org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO -> "Legado"
			type == org.skepsun.kototoro.core.jsonsource.SourceType.JSON_TVBOX -> "TVBox"
			type == org.skepsun.kototoro.core.jsonsource.SourceType.JSON_JS -> "JS"
			type == org.skepsun.kototoro.core.jsonsource.SourceType.EXTERNAL -> context.getString(R.string.external_source)
			type == org.skepsun.kototoro.core.jsonsource.SourceType.NATIVE -> null
			else -> null
		}
	}
}

fun ContentSource.getTitle(context: Context): String = when (val source = unwrap()) {
	is ContentParserSource -> source.title
	is KotatsuParserSource -> source.title
	LocalMangaSource -> context.getString(R.string.local_storage)
	LocalNovelSource -> "本地小说"
	TestContentSource -> context.getString(R.string.test_parser)
	is ExternalContentSource -> source.resolveName(context)
	is org.skepsun.kototoro.core.jsonsource.JsonContentSource -> source.displayName.ifBlank { source.name }
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> source.displayName
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> source.displayName
	is org.skepsun.kototoro.ireader.model.IReaderMangaSource -> source.displayName
	else -> {
		// Try to handle anonymous wrappers for JSON, Mihon, or Aniyomi sources
		if (source.name.startsWith("MIHON_")) {
			"Loading Mihon source..."
		} else if (source.name.startsWith("JSON_")) {
			"Loading JSON source..."
		} else if (source.name.startsWith("ANIYOMI_")) {
			"Loading Aniyomi source..."
		} else if (source.name.startsWith("TRACKING_")) {
			source.name.removePrefix("TRACKING_")
		} else {
			context.getString(R.string.unknown)
		}
	}
}

fun SpannableStringBuilder.appendIcon(textView: TextView, @DrawableRes resId: Int): SpannableStringBuilder {
	val icon = ContextCompat.getDrawable(textView.context, resId) ?: return this
	icon.setTintList(textView.textColors)
	val size = textView.lineHeight
	icon.setBounds(0, 0, size, size)
	val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ImageSpan.ALIGN_CENTER
	} else {
		ImageSpan.ALIGN_BOTTOM
	}
	return inSpans(ImageSpan(icon, alignment)) { append(' ') }
}

private class AnonymousContentSource(override val name: String) : ContentSource {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ContentSource) return false
		return name == other.name
	}

	override fun hashCode(): Int = name.hashCode()
	
	override fun toString(): String = "AnonymousContentSource(name=$name)"
}

/**
 * Maps IReader language/country codes to ISO 639-1 language codes.
 * IReader extensions use country codes (e.g., "cn") while Kototoro uses language codes (e.g., "zh").
 */
fun mapIReaderLangToLocale(lang: String): String? = when (lang.lowercase()) {
	"cn" -> "zh"
	"en" -> "en"
	"jp" -> "ja"
	"kr" -> "ko"
	"tw" -> "zh"
	"all" -> ""
	else -> lang  // Fallback: try using the code directly
}
