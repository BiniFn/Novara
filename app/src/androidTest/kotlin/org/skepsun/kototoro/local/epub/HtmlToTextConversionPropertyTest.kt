package org.skepsun.kototoro.local.epub

import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Property-based tests for HTML to text conversion.
 * 
 * Feature: epub-reader-improvements
 */
class HtmlToTextConversionPropertyTest : StringSpec({
    
    val reader = EpubReaderImpl()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val tempDir = File(context.cacheDir, "epub-test-html-${System.currentTimeMillis()}")
    
    beforeSpec {
        tempDir.mkdirs()
    }
    
    afterSpec {
        tempDir.deleteRecursively()
    }
    
    /**
     * Property 8: HTML to Text Conversion
     * Validates: Requirements 2.4
     * 
     * For any HTML content in EPUB chapters, 
     * the system SHALL extract readable text without HTML tags
     */
    "extracted text does not contain HTML tags".config(invocations = 100) {
        checkAll(
            Arb.int(1..10),
            Arb.list(Arb.string(1..100), 1..10)
        ) { chapterCount, textContents ->
            val testFile = File(tempDir, "test-html-${System.currentTimeMillis()}.epub")
            
            // Create HTML content with various tags
            val htmlContents = textContents.take(chapterCount).map { text ->
                """
                <!DOCTYPE html>
                <html>
                <head><title>Test</title></head>
                <body>
                    <h1>Title</h1>
                    <p>$text</p>
                    <div><span>More content</span></div>
                </body>
                </html>
                """.trimIndent()
            }
            
            EpubTestHelper.createTestEpub(testFile, chapterCount, chapterContents = htmlContents)
            
            val content = runBlocking { reader.readEpub(testFile) }
            
            // Verify no HTML tags in extracted text
            content?.chapters?.forEach { chapter ->
                chapter.content shouldNotContain "<html>"
                chapter.content shouldNotContain "<body>"
                chapter.content shouldNotContain "<p>"
                chapter.content shouldNotContain "</p>"
                chapter.content shouldNotContain "<div>"
                chapter.content shouldNotContain "<span>"
                chapter.content shouldNotContain "<h1>"
            }
            
            testFile.delete()
        }
    }
    
    "script and style tags are completely removed with their content".config(invocations = 50) {
        val testFile = File(tempDir, "test-script-style-${System.currentTimeMillis()}.epub")
        
        val htmlWithScriptAndStyle = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { color: red; }
                    .hidden { display: none; }
                </style>
                <script>
                    console.log('This should not appear');
                    alert('Neither should this');
                </script>
            </head>
            <body>
                <p>Visible content</p>
                <script>document.write('Hidden script');</script>
                <style>.more { color: blue; }</style>
            </body>
            </html>
        """.trimIndent()
        
        EpubTestHelper.createTestEpub(testFile, 1, chapterContents = listOf(htmlWithScriptAndStyle))
        
        val content = runBlocking { reader.readEpub(testFile) }
        
        val text = content?.chapters?.firstOrNull()?.content ?: ""
        
        // Verify script and style content is removed
        text shouldNotContain "color: red"
        text shouldNotContain "console.log"
        text shouldNotContain "alert"
        text shouldNotContain "document.write"
        text shouldNotContain ".hidden"
        
        // Verify visible content is preserved
        text shouldContain "Visible content"
        
        testFile.delete()
    }
    
    "HTML entities are decoded correctly".config(invocations = 50) {
        val testFile = File(tempDir, "test-entities-${System.currentTimeMillis()}.epub")
        
        val htmlWithEntities = """
            <!DOCTYPE html>
            <html>
            <body>
                <p>&lt;tag&gt; &amp; &quot;quotes&quot; &nbsp; &#39;apostrophe&#39;</p>
            </body>
            </html>
        """.trimIndent()
        
        EpubTestHelper.createTestEpub(testFile, 1, chapterContents = listOf(htmlWithEntities))
        
        val content = runBlocking { reader.readEpub(testFile) }
        
        val text = content?.chapters?.firstOrNull()?.content ?: ""
        
        // Verify entities are decoded
        text shouldContain "<tag>"
        text shouldContain "&"
        text shouldContain "\""
        text shouldContain "'"
        
        // Verify entity codes are not in output
        text shouldNotContain "&lt;"
        text shouldNotContain "&gt;"
        text shouldNotContain "&amp;"
        text shouldNotContain "&quot;"
        text shouldNotContain "&#39;"
        
        testFile.delete()
    }
    
    "br and p tags are converted to newlines".config(invocations = 50) {
        val testFile = File(tempDir, "test-newlines-${System.currentTimeMillis()}.epub")
        
        val htmlWithBreaks = """
            <!DOCTYPE html>
            <html>
            <body>
                <p>First paragraph</p>
                <p>Second paragraph</p>
                Line one<br/>
                Line two<br>
                Line three
            </body>
            </html>
        """.trimIndent()
        
        EpubTestHelper.createTestEpub(testFile, 1, chapterContents = listOf(htmlWithBreaks))
        
        val content = runBlocking { reader.readEpub(testFile) }
        
        val text = content?.chapters?.firstOrNull()?.content ?: ""
        
        // Verify text contains newlines (paragraphs are separated)
        text shouldContain "First paragraph"
        text shouldContain "Second paragraph"
        text shouldContain "Line one"
        text shouldContain "Line two"
        text shouldContain "Line three"
        
        // Verify HTML tags are removed
        text shouldNotContain "<p>"
        text shouldNotContain "</p>"
        text shouldNotContain "<br"
        
        testFile.delete()
    }
    
    "excessive whitespace is normalized".config(invocations = 50) {
        val testFile = File(tempDir, "test-whitespace-${System.currentTimeMillis()}.epub")
        
        val htmlWithWhitespace = """
            <!DOCTYPE html>
            <html>
            <body>
                <p>Text    with     multiple     spaces</p>
                <p>
                
                
                Multiple
                
                
                newlines
                
                
                </p>
            </body>
            </html>
        """.trimIndent()
        
        EpubTestHelper.createTestEpub(testFile, 1, chapterContents = listOf(htmlWithWhitespace))
        
        val content = runBlocking { reader.readEpub(testFile) }
        
        val text = content?.chapters?.firstOrNull()?.content ?: ""
        
        // Verify multiple spaces are reduced to single space
        text shouldNotContain "    "
        text shouldNotContain "     "
        
        // Verify excessive newlines are reduced (max 2 consecutive)
        text shouldNotContain "\n\n\n"
        
        testFile.delete()
    }
    
    "extractTextFromHtml method works correctly with byte arrays".config(invocations = 50) {
        val htmlBytes = EpubTestHelper.generateComplexHtmlContent().toByteArray(Charsets.UTF_8)
        
        val text = reader.extractTextFromHtml(htmlBytes)
        
        // Verify text is extracted
        text shouldContain "Title"
        text shouldContain "First paragraph"
        text shouldContain "Second paragraph"
        text shouldContain "bold"
        text shouldContain "italic"
        text shouldContain "Nested content"
        
        // Verify HTML tags are removed
        text shouldNotContain "<h1>"
        text shouldNotContain "<p>"
        text shouldNotContain "<strong>"
        text shouldNotContain "<em>"
        text shouldNotContain "<div>"
        text shouldNotContain "<span>"
        
        // Verify script and style are removed
        text shouldNotContain "console.log"
        text shouldNotContain "color: black"
    }
})
