package org.skepsun.kototoro.core.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import org.skepsun.kototoro.core.model.MangaSource
import org.skepsun.kototoro.core.util.ext.readParcelableCompat
import org.skepsun.kototoro.core.util.ext.readSerializableCompat
import org.skepsun.kototoro.core.util.ext.readStringSet
import org.skepsun.kototoro.core.util.ext.writeStringSet
import org.skepsun.kototoro.parsers.model.Manga

@Parcelize
data class ParcelableManga(
	val manga: Manga,
	private val withDescription: Boolean = true,
	private val withChapters: Boolean = false,
) : Parcelable {

	companion object : Parceler<ParcelableManga> {

		override fun ParcelableManga.write(parcel: Parcel, flags: Int): Unit = with(manga) {
			parcel.writeLong(id)
			parcel.writeString(title)
			parcel.writeStringSet(altTitles)
			parcel.writeString(url)
			parcel.writeString(publicUrl)
			parcel.writeFloat(rating)
			parcel.writeSerializable(contentRating)
			parcel.writeString(coverUrl)
			parcel.writeString(largeCoverUrl)
			parcel.writeString(description.takeIf { withDescription })
			parcel.writeParcelable(ParcelableMangaTags(tags), flags)
			parcel.writeSerializable(state)
			parcel.writeStringSet(authors)
			parcel.writeString(source.name)
			// Write chapters if requested
			val chaptersToWrite = if (withChapters) chapters else null
			parcel.writeInt(chaptersToWrite?.size ?: -1)
			chaptersToWrite?.forEach { chapter ->
				parcel.writeLong(chapter.id)
				parcel.writeString(chapter.title)
				parcel.writeFloat(chapter.number)
				parcel.writeInt(chapter.volume)
				parcel.writeString(chapter.url)
				parcel.writeString(chapter.scanlator)
				parcel.writeLong(chapter.uploadDate)
				parcel.writeString(chapter.branch)
			}
		}

		override fun create(parcel: Parcel): ParcelableManga {
			val id = parcel.readLong()
			val title = requireNotNull(parcel.readString())
			val altTitles = parcel.readStringSet()
			val url = requireNotNull(parcel.readString())
			val publicUrl = requireNotNull(parcel.readString())
			val rating = parcel.readFloat()
			val contentRating = parcel.readSerializableCompat<org.skepsun.kototoro.parsers.model.ContentRating>()
			val coverUrl = parcel.readString()
			val largeCoverUrl = parcel.readString()
			val description = parcel.readString()
			val tags = requireNotNull(parcel.readParcelableCompat<ParcelableMangaTags>()).tags
			val state = parcel.readSerializableCompat<org.skepsun.kototoro.parsers.model.MangaState>()
			val authors = parcel.readStringSet()
			val sourceName = requireNotNull(parcel.readString())
			
			// Read chapters if present
			val chaptersSize = parcel.readInt()
			val chapters = if (chaptersSize >= 0) {
				List(chaptersSize) {
					org.skepsun.kototoro.parsers.model.MangaChapter(
						id = parcel.readLong(),
						title = parcel.readString(),
						number = parcel.readFloat(),
						volume = parcel.readInt(),
						url = requireNotNull(parcel.readString()),
						scanlator = parcel.readString(),
						uploadDate = parcel.readLong(),
						branch = parcel.readString(),
						source = MangaSource(sourceName),
					)
				}
			} else {
				null
			}
			
			return ParcelableManga(
				Manga(
					id = id,
					title = title,
					altTitles = altTitles,
					url = url,
					publicUrl = publicUrl,
					rating = rating,
					contentRating = contentRating,
					coverUrl = coverUrl,
					largeCoverUrl = largeCoverUrl,
					description = description,
					tags = tags,
					state = state,
					authors = authors,
					chapters = chapters,
					source = MangaSource(sourceName),
				),
				withDescription = true,
				withChapters = chapters != null,
			)
		}
	}
}
