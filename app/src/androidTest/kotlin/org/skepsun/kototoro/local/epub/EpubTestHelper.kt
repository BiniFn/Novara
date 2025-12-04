package org.skepsun.kototoro.local.epub

import me.ag2s.epublib.domain.Book
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.SpineReference
import me.ag2s.epublib.epub.EpubWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for creating test EPUB files for property-based testing.
 */
object EpubTestHelper {
    
    /**
     * Creates a test EPUB file with the specified number of chapters.
     * 
     * @param file The file to write the EPUB to
     * @param chapterCount Number of chapters to include
     * @param chapterTitles Optional list of chapter titles (if null, chapters will have no titles)
     * @param chapterContents Optional list of chapter HTML contents (if null, generates simple content)
     */
    fun createTestEpub(
        file: File,
        chapterCount: Int,
        chapterTitles: List<String?>? = null,
        chapterContents: List<String>? = null
    ) {
        val book = Book()
        book.metadata.addTitle("Test Book")
        book.metadata.addAuthor(me.ag2s.epublib.domain.Author("Test", "Author"))
        
        // Add chapters to the book
        for (i in 0 until chapterCount) {
            val title = chapterTitles?.getOrNull(i)
            val content = chapterContents?.getOrNull(i) ?: generateSimpleHtmlContent(i)
            
            val resource = Resource(
                content.toByteArray(Charsets.UTF_8),
                "chapter$i.html"
            )
            
            // Set title on the resource
            // The title property is set directly on the Resource object
            if (title != null && title.isNotBlank()) {
                resource.title = title
            }
            
            // Add to book - addSection also sets the title in the TOC
            book.addSection(title ?: "", resource)
        }
        
        // Write the EPUB file
        val writer = EpubWriter()
        FileOutputStream(file).use { outputStream ->
            writer.write(book, outputStream)
        }
    }
    
    /**
     * Generates simple HTML content for a chapter.
     */
    private fun generateSimpleHtmlContent(chapterIndex: Int): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Chapter ${chapterIndex + 1}</title>
            </head>
            <body>
                <h1>Chapter ${chapterIndex + 1}</h1>
                <p>This is the content of chapter ${chapterIndex + 1}.</p>
                <p>It contains some text for testing purposes.</p>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * Generates HTML content with various HTML elements for testing HTML to text conversion.
     */
    fun generateComplexHtmlContent(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Complex Chapter</title>
                <style>body { color: black; }</style>
                <script>console.log('test');</script>
            </head>
            <body>
                <h1>Title</h1>
                <p>First paragraph.</p>
                <br/>
                <p>Second paragraph with <strong>bold</strong> and <em>italic</em> text.</p>
                <div>
                    <span>Nested content</span>
                </div>
                <p>HTML entities: &lt;tag&gt; &amp; &quot;quotes&quot; &nbsp; &#39;apostrophe&#39;</p>
            </body>
            </html>
        """.trimIndent()
    }
}
