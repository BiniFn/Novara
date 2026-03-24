package org.skepsun.kototoro.extensions.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Test

class IReaderRepoTest {

    @Serializable
    private data class IReaderExtensionIndexDto(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val code: Long,
        val version: String,
        val nsfw: Boolean,
    )

    @Test
    fun testParse() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
            coerceInputValues = true
        }
        val body = java.net.URL("https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/index.min.json").readText()
        val dto = json.decodeFromString<List<IReaderExtensionIndexDto>>(body)
        println("Parsed ${dto.size}")
    }
}
