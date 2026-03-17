package org.skepsun.kototoro.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.skepsun.kototoro.parsers.model.ContentSource

object ContentSourceSerializer : KSerializer<ContentSource> {

    override val descriptor: SerialDescriptor = serialDescriptor<String>()

    override fun serialize(
        encoder: Encoder,
        value: ContentSource
    ) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ContentSource = ContentSource(decoder.decodeString())
}
