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
		source = ContentSource(parcel.readString()),
	)

	override fun ContentPage.write(parcel: Parcel, flags: Int) {
		parcel.writeLong(id)
		parcel.writeString(url)
		parcel.writeString(preview)
		parcel.writeString(source.name)
	}
}

@Parcelize
@TypeParceler<ContentPage, ContentPageParceler>
class ParcelableContentPage(val page: ContentPage) : Parcelable
