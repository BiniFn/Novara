package org.skepsun.kototoro.details.ui

import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

/**
 * Test ContentSource for property tests
 */
object TestContentSource : ContentSource {
    override val name: String = "TestSource"
    override val locale: String = "en"
    override val contentType: ContentType = ContentType.MANGA
}
