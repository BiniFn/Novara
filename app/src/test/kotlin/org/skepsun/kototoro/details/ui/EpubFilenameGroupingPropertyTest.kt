package org.skepsun.kototoro.details.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.skepsun.kototoro.local.epub.ChapterMetadata
import org.skepsun.kototoro.local.epub.ChapterType
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentSource

/**
 * Property-based tests for EPUB filename grouping.
 * 
 * Feature: epub-reader-improvements
 */
class EpubFilenameGroupingPropertyTest : StringSpec({
    
    /**
     * Property 14: EPUB Filename Grouping
     * Validates: Requirements 4.2
     * 
     * For any set of EPUB_INTERNAL chapters with the same epubFileName, 
     * they SHALL be grouped together
     */
    "EPUB_INTERNAL chapters with same filename are grouped together".config(invocations = 10) {
        checkAll(arbChapterListWithEpubInternal()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Group EPUB_INTERNAL chapters by filename
            val epubInternalByFile = chapters
                .filter { metadataMap[it.id]?.chapterType == ChapterType.EPUB_INTERNAL }
                .groupBy { metadataMap[it.id]?.epubFileName ?: "Unknown" }
            
            // For each EPUB filename, verify there's a corresponding group
            epubInternalByFile.forEach { (epubFileName, expectedChapters) ->
                val group = groups.find { it.name == epubFileName }
                group shouldBe group // Should not be null
                
                group?.let {
                    // All chapters with this filename should be in the group
                    it.chapters.size shouldBe expectedChapters.size
                    expectedChapters.forEach { chapter ->
                        it.chapters shouldContain chapter
                    }
                    
                    // The group should be collapsible
                    it.isCollapsible shouldBe true
                }
            }
        }
    }
    
    /**
     * Property: EPUB file groups are collapsible
     * 
     * For any EPUB file group, it SHALL be marked as collapsible
     */
    "EPUB file groups are collapsible".config(invocations = 10) {
        checkAll(arbChapterListWithEpubInternal().filter { (chapters, _) -> 
            chapters.isNotEmpty() 
        }) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Find all EPUB file groups (not "下载链接" or "在线章节")
            val epubFileGroups = groups.filter { 
                it.name != "下载链接" && it.name != "在线章节" 
            }
            
            epubFileGroups.forEach { group ->
                group.isCollapsible shouldBe true
            }
        }
    }
    
    /**
     * Property: Different EPUB filenames create separate groups
     * 
     * For any two different EPUB filenames, their chapters SHALL be in separate groups
     */
    "different EPUB filenames create separate groups".config(invocations = 10) {
        checkAll(arbChapterListWithMultipleEpubs()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Get all unique EPUB filenames
            val epubFilenames = chapters
                .mapNotNull { metadataMap[it.id] }
                .filter { it.chapterType == ChapterType.EPUB_INTERNAL }
                .mapNotNull { it.epubFileName }
                .distinct()
            
            // Each filename should have its own group
            epubFilenames.forEach { filename ->
                val group = groups.find { it.name == filename }
                group shouldBe group // Should not be null
                
                // Verify all chapters in this group have the same filename
                group?.chapters?.forEach { chapter ->
                    val metadata = metadataMap[chapter.id]
                    metadata?.epubFileName shouldBe filename
                }
            }
        }
    }
    
    /**
     * Property: No EPUB_INTERNAL chapters in wrong groups
     * 
     * For any EPUB_INTERNAL chapter, it SHALL only appear in its corresponding filename group
     */
    "EPUB_INTERNAL chapters only in their filename group".config(invocations = 10) {
        checkAll(arbChapterListWithEpubInternal()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // For each EPUB_INTERNAL chapter, verify it's in the correct group
            chapters.forEach { chapter ->
                val metadata = metadataMap[chapter.id]
                if (metadata?.chapterType == ChapterType.EPUB_INTERNAL) {
                    val expectedGroupName = metadata.epubFileName ?: "Unknown EPUB"
                    
                    // Find which group contains this chapter
                    val containingGroups = groups.filter { it.chapters.contains(chapter) }
                    
                    // Should be in exactly one group
                    containingGroups.size shouldBe 1
                    
                    // That group should have the correct name
                    containingGroups.first().name shouldBe expectedGroupName
                }
            }
        }
    }
})

/**
 * Arbitrary generator for chapter list with EPUB_INTERNAL chapters
 */
private fun arbChapterListWithEpubInternal(): Arb<Pair<List<ContentChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val epubInternalCount = (Arb.int(1..20).bind())
    val normalCount = (Arb.int(0..10).bind())
    
    val chapters = mutableListOf<ContentChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    // Add EPUB_INTERNAL chapters (use 2-3 different filenames)
    val epubFilenames = listOf("volume_1.epub", "volume_2.epub", "volume_3.epub")
    repeat(epubInternalCount) {
        val chapterId = Arb.long(1L..1000000L).bind()
        val parentId = Arb.long(1000001L..2000000L).bind()
        val epubFileName = epubFilenames[it % epubFilenames.size]
        
        val chapter = ContentChapter(
            id = chapterId,
            title = "Chapter ${it + 1}",
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
    
    // Add NORMAL chapters
    repeat(normalCount) {
        val chapterId = Arb.long(2000001L..3000000L).bind()
        val chapter = ContentChapter(
            id = chapterId,
            title = "Online Chapter ${it + 1}",
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
    
    Pair(chapters, metadataMap)
}

/**
 * Arbitrary generator for chapter list with multiple different EPUB files
 */
private fun arbChapterListWithMultipleEpubs(): Arb<Pair<List<ContentChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val epubFileCount = (Arb.int(2..5).bind())
    val chaptersPerEpub = (Arb.int(2..5).bind())
    
    val chapters = mutableListOf<ContentChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    var chapterIdCounter = 1L
    
    // Create multiple EPUB files with chapters
    repeat(epubFileCount) { epubIndex ->
        val epubFileName = "volume_${epubIndex + 1}.epub"
        val parentId = Arb.long(1000000L + epubIndex * 1000L..1000000L + (epubIndex + 1) * 1000L).bind()
        
        repeat(chaptersPerEpub) { chapterIndex ->
            val chapterId = chapterIdCounter++
            val chapter = ContentChapter(
                id = chapterId,
                title = "Chapter ${chapterIndex + 1}",
                number = (chapterIndex + 1).toFloat(),
                volume = epubIndex + 1,
                url = "file:///path/to/${epubFileName}#chapter/${chapterIndex}",
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
    }
    
    Pair(chapters, metadataMap)
}
