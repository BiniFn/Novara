@file:Suppress("unused", "FunctionName")

package kotlinx.serialization.json.okio

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * Lightweight shims for kotlinx-serialization-json-okio to avoid NoClassDefFoundError
 * when extensions expect these helpers but the artifact is missing on device.
 *
 * These implementations simply bridge through UTF-8 strings.
 */
fun <T> Json.decodeFromBufferedSource(
    deserializer: DeserializationStrategy<T>,
    source: BufferedSource,
): T {
    val content = source.readUtf8()
    return decodeFromString(deserializer, content)
}

fun <T> Json.encodeToBufferedSink(
    serializer: SerializationStrategy<T>,
    value: T,
    sink: BufferedSink,
) {
    val content = encodeToString(serializer, value)
    sink.writeUtf8(content)
}
