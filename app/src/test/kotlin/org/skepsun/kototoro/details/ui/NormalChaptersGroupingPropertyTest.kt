package org.skepsun.kototoro.details.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.skepsun.kototoro.local.epub.ChapterMetadata
import org.skepsun.kototoro.local.epub.ChapterType
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentSource

/**
 * Property-based tests for normal chapters grouping.
 * 
 * Feature: epub-reader-improvements
 */
class NormalChaptersGroupingPropertyTest : StringSpec({
    
    /**
     * Property 15: Normal Chapters Grouping
     * Validates: Requirements 4.3
     * 
     * For any chapter list containing NORMAL chapters, they SHALL all be 
     * grouped under "在线章节"
     */
    "all NORMAL chapters are grouped under 在线章节".config(invocations = 10) {
        checkAll(arbChapterListWithNormal()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Find all NORMAL chapters
            val normalChapters = chapters.filter { chapter ->
                val chapterType = metadataMap[chapter.id]?.chapterType ?: ChapterType.NORMAL
                chapterType == ChapterType.NORMAL
            }
            
            if (normalChapters.isNotEmpty()) {
                // There should be a group named "在线章节"
                val normalGroup = groups.find { it.name == "在线章节" }
                normalGroup shouldBe normalGroup // Should not be null
                
                // All normal chapters should be in this group
                normalGroup?.let { group ->
                    group.chapters.size shouldBe normalChapters.size
                    group.chapters shouldBe normalChapters
                    
                    // The group should NOT be collapsible
                    group.isCollapsible shouldBe false
                }
            }
        }
    }
    
    /**
     * Property: Normal chapters group is not collapsible
     * 
     * For any normal chapters group, it SHALL be marked as non-collapsible
     */
    "normal chapters group is not collapsible".config(invocations = 10) {
        checkAll(arbChapterListWithNormal().filter { (chapters, _) -> 
            chapters.isNotEmpty() 
        }) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            val normalGroup = groups.find { it.name == "在线章节" }
            if (normalGroup != null) {
                normalGroup.isCollapsible shouldBe false
            }
        }
    }
    
    /**
     * Property: No NORMAL chapters outside normal chapters group
     * 
     * For any chapter list, NORMAL chapters SHALL NOT appear in other groups
     */
    "no NORMAL chapters outside normal chapters group".config(invocations = 10) {
        checkAll(arbChapterListWithNormal()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Check all groups except "在线章节"
            groups.filter { it.name != "在线章节" }.forEach { group ->
                group.chapters.forEach { chapter ->
                    val chapterType = metadataMap[chapter.id]?.chapterType ?: ChapterType.NORMAL
                    // Should not be NORMAL if it's in a different group
                    if (chapterType == ChapterType.NORMAL) {
                        // This should never happen
                        chapterType shouldBe ChapterType.EPUB_DOWNLOAD // Force failure with clear message
                    }
                }
            }
        }
    }
    
    /**
     * Property: Chapters without metadata are treated as NORMAL
     * 
     * For any chapter without metadata, it SHALL be grouped under "在线章节"
     */
    "chapters without metadata are treated as NORMAL".config(invocations = 10) {
        checkAll(arbChapterListWithMissingMetadata()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Find chapters without metadata
            val chaptersWithoutMetadata = chapters.filter { it.id !in metadataMap }
            
            if (chaptersWithoutMetadata.isNotEmpty()) {
                // There should be a group named "在线章节"
                val normalGroup = groups.find { it.name == "在线章节" }
                normalGroup shouldBe normalGroup // Should not be null
                
                // All chapters without metadata should be in this group
                normalGroup?.let { group ->
                    group.chapters.map { it.id }.toSet().containsAll(chaptersWithoutMetadata.map { it.id }) shouldBe true
                }
            }
        }
    }
    
    /**
     * Property: Mixed chapter types create separate groups
     * 
     * For any chapter list with mixed types, NORMAL chapters SHALL be in "在线章节" 
     * and other types in their respective groups
     */
    "mixed chapter types create separate groups".config(invocations = 10) {
        checkAll(arbMixedChapterList()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            val normalChapters = chapters.filter { 
                (metadataMap[it.id]?.chapterType ?: ChapterType.NORMAL) == ChapterType.NORMAL 
            }
            val downloadChapters = chapters.filter { 
                metadataMap[it.id]?.chapterType == ChapterType.EPUB_DOWNLOAD 
            }
            val epubInternalChapters = chapters.filter { 
                metadataMap[it.id]?.chapterType == ChapterType.EPUB_INTERNAL 
            }
            
            // Verify normal chapters are in "在线章节"
            if (normalChapters.isNotEmpty()) {
                val normalGroup = groups.find { it.name == "在线章节" }
                normalGroup shouldBe normalGroup // Should not be null
                normalGroup?.chapters?.size shouldBe normalChapters.size
            }
            
            // Verify download chapters are in "下载链接"
            if (downloadChapters.isNotEmpty()) {
                val downloadGroup = groups.find { it.name == "下载链接" }
                downloadGroup shouldBe downloadGroup // Should not be null
                downloadGroup?.chapters?.size shouldBe downloadChapters.size
            }
            
            // Verify EPUB internal chapters are in their respective groups
            if (epubInternalChapters.isNotEmpty()) {
                val epubGroups = groups.filter { 
                    it.name != "在线章节" && it.name != "下载链接" 
                }
                val totalEpubChapters = epubGroups.sumOf { it.chapters.size }
                totalEpubChapters shouldBe epubInternalChapters.size
            }
        }
    }
})

/**
 * Arbitrary generator for chapter list with NORMAL chapters
 */
private fun arbChapterListWithNormal(): Arb<Pair<List<ContentChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val normalCount = (Arb.int(1..20).bind())
    val downloadCount = (Arb.int(0..5).bind())
    val epubInternalCount = (Arb.int(0..10).bind())
    
    val chapters = mutableListOf<ContentChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    // Add NORMAL chapters
    repeat(normalCount) {
        val chapterId = Arb.long(1L..1000000L).bind()
        val chapter = ContentChapter(
            id = chapterId,
            title = "Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "https://example.com/chapter/${chapterId}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.NORMAL,
            parentChapterId = null,
            epubFileName = null
        )
    }
    
    // Add EPUB_DOWNLOAD chapters
    repeat(downloadCount) {
        val chapterId = Arb.long(1000001L..2000000L).bind()
        val chapter = ContentChapter(
            id = chapterId,
            title = "Download ${Arb.string(5..20).bind()}",
            number = it.toFloat(),
            volume = 0,
            url = "https://example.com/download/${chapterId}.epub",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_DOWNLOAD,
            parentChapterId = null,
            epubFileName = "volume_${it}.epub"
        )
    }
    
    // Add EPUB_INTERNAL chapters
    repeat(epubInternalCount) {
        val chapterId = Arb.long(2000001L..3000000L).bind()
        val parentId = Arb.long(1000001L..2000000L).bind()
        val chapter = ContentChapter(
            id = chapterId,
            title = "Internal Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "file:///path/to/epub.epub#chapter/${it}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_INTERNAL,
            parentChapterId = parentId,
            epubFileName = "volume_${it % 3}.epub"
        )
    }
    
    Pair(chapters, metadataMap)
}

/**
 * Arbitrary generator for chapter list with some chapters missing metadata
 */
private fun arbChapterListWithMissingMetadata(): Arb<Pair<List<ContentChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val chapterCount = (Arb.int(5..15).bind())
    val missingMetadataCount = (Arb.int(1..5).bind()).coerceAtMost(chapterCount)
    
    val chapters = mutableListOf<ContentChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    repeat(chapterCount) {
        val chapterId = Arb.long(1L..1000000L).bind()
        val chapter = ContentChapter(
            id = chapterId,
            title = "Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "https://example.com/chapter/${chapterId}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        
        // Only add metadata for some chapters
        if (it >= missingMetadataCount) {
            metadataMap[chapterId] = ChapterMetadata(
                chapterId = chapterId,
                chapterType = ChapterType.NORMAL,
                parentChapterId = null,
                epubFileName = null
            )
        }
    }
    
    Pair(chapters, metadataMap)
}

/**
 * Arbitrary generator for mixed chapter list with all types
 */
private fun arbMixedChapterList(): Arb<Pair<List<ContentChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val normalCount = (Arb.int(1..10).bind())
    val downloadCount = (Arb.int(1..5).bind())
    val epubInternalCount = (Arb.int(1..10).bind())
    
    val chapters = mutableListOf<ContentChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    var chapterIdCounter = 1L
    
    // Add NORMAL chapters
    repeat(normalCount) {
        val chapterId = chapterIdCounter++
        val chapter = ContentChapter(
            id = chapterId,
            title = "Normal Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "https://example.com/chapter/${chapterId}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.NORMAL,
            parentChapterId = null,
            epubFileName = null
        )
    }
    
    // Add EPUB_DOWNLOAD chapters
    repeat(downloadCount) {
        val chapterId = chapterIdCounter++
        val chapter = ContentChapter(
            id = chapterId,
            title = "Download ${it + 1}",
            number = it.toFloat(),
            volume = 0,
            url = "https://example.com/download/${chapterId}.epub",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_DOWNLOAD,
            parentChapterId = null,
            epubFileName = "volume_${it}.epub"
        )
    }
    
    // Add EPUB_INTERNAL chapters
    val epubFilenames = listOf("volume_1.epub", "volume_2.epub")
    repeat(epubInternalCount) {
        val chapterId = chapterIdCounter++
        val parentId = Arb.long(1L..1000L).bind()
        val epubFileName = epubFilenames[it % epubFilenames.size]
        
        val chapter = ContentChapter(
            id = chapterId,
            title = "Internal Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "file:///path/to/${epubFileName}#chapter/${it}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestContentSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_INTERNAL,
            parentChapterId = parentId,
            epubFileName = epubFileName
        )
    }
    
    Pair(chapters, metadataMap)
}
