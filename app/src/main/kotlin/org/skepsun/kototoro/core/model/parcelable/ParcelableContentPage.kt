package org.skepsun.kototoro.core.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentPage

object ContentPageParceler : Parceler<ContentPage> {
	override fun create(parcel: Parcel) = ContentPage(
		id = parcel.readLong(),
		url = requireNotNull(parcel.readString()),
		preview = parcel.readString(),
		headers = parcel.readStringMap(),
		source = ContentSource(parcel.readString()),
	)

	override fun ContentPage.write(parcel: Parcel, flags: Int) {
		parcel.writeLong(id)
		parcel.writeString(url)
		parcel.writeString(preview)
		parcel.writeStringMap(headers)
		parcel.writeString(source.name)
	}

	private fun Parcel.readStringMap(): Map<String, String>? {
		val size = readInt()
		if (size < 0) return null
		return buildMap(size) {
			repeat(size) {
				val key = readString() ?: return@repeat
				val value = readString().orEmpty()
				put(key, value)
			}
		}
	}

	private fun Parcel.writeStringMap(map: Map<String, String>?) {
		if (map == null) {
			writeInt(-1)
			return
		}
		writeInt(map.size)
		map.forEach { (key, value) ->
			writeString(key)
			writeString(value)
		}
	}
}

@Parcelize
@TypeParceler<ContentPage, ContentPageParceler>
class ParcelableContentPage(val page: ContentPage) : Parcelable
