package org.skepsun.kototoro.local.epub

import me.ag2s.epublib.domain.Book
import me.ag2s.epublib.epub.EpubReader

/**
 * 测试epublib库是否正确导入
 */
object EpubTest {
    fun testEpubLib() {
        // 这只是一个编译测试，确保epublib类可以被引用
        val reader = EpubReader()
        val book: Book? = null
        println("EpubLib is available: reader=$reader, book=$book")
    }
}
