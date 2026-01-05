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
import org.skepsun.kototoro.core.parser.external.ExternalMangaSource
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.toLocale
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.util.splitTwoParts
import java.util.Locale

data object LocalMangaSource : MangaSource {
	override val name = "LOCAL"
}

data object LocalNovelSource : MangaSource {
	override val name = "LOCAL_NOVEL"
}

data object UnknownMangaSource : MangaSource {
	override val name = "UNKNOWN"
}

data object TestMangaSource : MangaSource {
	override val name = "TEST"
}

fun MangaSource(name: String?): MangaSource {
	when (name ?: return UnknownMangaSource) {
	UnknownMangaSource.name -> return UnknownMangaSource
	LocalMangaSource.name -> return LocalMangaSource
	LocalNovelSource.name -> return LocalNovelSource
	TestMangaSource.name -> return TestMangaSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownMangaSource
		return ExternalMangaSource(packageName = parts.first, authority = parts.second)
	}
	// Check if it's a JSON source (starts with JSON_ prefix)
	if (name.startsWith("JSON_")) {
		android.util.Log.d("MangaSource", "Detected JSON source name: $name, returning stable MangaSource")
		return AnonymousMangaSource(name)
	}
	// Check if it's a Mihon source (starts with MIHON_ prefix)
	if (name.startsWith("MIHON_")) {
		android.util.Log.d("MangaSource", "Detected Mihon source name: $name, returning stable MangaSource")
		return AnonymousMangaSource(name)
	}
	// Check if it's an Aniyomi source (starts with ANIYOMI_ prefix)
	if (name.startsWith("ANIYOMI_")) {
		android.util.Log.d("MangaSource", "Detected Aniyomi source name: $name, returning stable MangaSource")
		return AnonymousMangaSource(name)
	}
	MangaParserSource.entries.forEach {
		if (it.name == name) return it
	}
	return UnknownMangaSource
}

fun Collection<String>.toMangaSources() = map(::MangaSource)

fun MangaSource.isNsfw(): Boolean = when (this) {
	is MangaSourceInfo -> mangaSource.isNsfw()
	is MangaParserSource -> contentType in setOf(
		ContentType.HENTAI_MANGA,
		ContentType.HENTAI_NOVEL,
		ContentType.HENTAI_VIDEO,
	)
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> isNsfw
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> isNsfw
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

tailrec fun MangaSource.unwrap(): MangaSource = if (this is MangaSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun MangaSource.getLocale(): Locale? = (unwrap() as? MangaParserSource)?.locale?.toLocaleOrNull()

fun MangaSource.getContentType(): ContentType = when (val source = unwrap()) {
	is MangaParserSource -> source.contentType
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> if (source.isNsfw) ContentType.HENTAI_MANGA else ContentType.MANGA
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> if (source.isNsfw) ContentType.HENTAI_VIDEO else ContentType.VIDEO
	else -> {
		// Fallback for serialized sources that lost their type info (e.g., through Parcelable)
		// Detect by name prefix
		val sourceName = source.name
		when {
			sourceName.startsWith("ANIYOMI_") -> ContentType.VIDEO  // Aniyomi sources are always video
			sourceName.startsWith("JSON_TVBOX_") -> ContentType.VIDEO
			sourceName.startsWith("JSON_LEGADO_") -> ContentType.NOVEL
			else -> ContentType.MANGA
		}
	}
}

fun MangaSource.getSummary(context: Context): String? = when (val source = unwrap()) {
	is MangaParserSource,
	is org.skepsun.kototoro.mihon.model.MihonMangaSource,
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> {
		val contentType = getContentType()
		val type = context.getString(contentType.titleResId)
		val lang = when (source) {
			is MangaParserSource -> source.locale.toLocale()
			is org.skepsun.kototoro.mihon.model.MihonMangaSource -> source.language.toLocale()
			is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> source.language.toLocale()
			else -> Locale.getDefault()
		}
		val locale = lang.getDisplayName(context)
		context.getString(R.string.source_summary_pattern, type, locale)
	}

	is ExternalMangaSource -> context.getString(R.string.external_source)
	
	is org.skepsun.kototoro.core.jsonsource.JsonMangaSource -> {
		val sourceTypeIdentifier = org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier()
		val sourceType = sourceTypeIdentifier.getSourceType(source.name)
		when (sourceType) {
			org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO -> "Legado JSON Source"
			org.skepsun.kototoro.core.jsonsource.SourceType.JSON_TVBOX -> "TVBox JSON Source"
			else -> "JSON Source"
		}
	}

	else -> null
}

fun MangaSource.getTitle(context: Context): String = when (val source = unwrap()) {
	is MangaParserSource -> source.title
	LocalMangaSource -> context.getString(R.string.local_storage)
	LocalNovelSource -> "本地小说"
	TestMangaSource -> context.getString(R.string.test_parser)
	is ExternalMangaSource -> source.resolveName(context)
	is org.skepsun.kototoro.core.jsonsource.JsonMangaSource -> source.displayName.ifBlank { source.name }
	is org.skepsun.kototoro.mihon.model.MihonMangaSource -> source.displayName
	is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> source.displayName
	else -> {
		// Try to handle anonymous wrappers for JSON, Mihon, or Aniyomi sources
		if (source.name.startsWith("MIHON_")) {
			"Loading Mihon source..."
		} else if (source.name.startsWith("JSON_")) {
			"Loading JSON source..."
		} else if (source.name.startsWith("ANIYOMI_")) {
			"Loading Aniyomi source..."
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

private class AnonymousMangaSource(override val name: String) : MangaSource {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is MangaSource) return false
		return name == other.name
	}

	override fun hashCode(): Int = name.hashCode()
	
	override fun toString(): String = "AnonymousMangaSource(name=$name)"
}
