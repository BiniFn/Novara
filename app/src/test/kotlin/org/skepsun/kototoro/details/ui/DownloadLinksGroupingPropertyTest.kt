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
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Property-based tests for download links grouping.
 * 
 * Feature: epub-reader-improvements
 */
class DownloadLinksGroupingPropertyTest : StringSpec({
    
    /**
     * Property 13: Download Links Grouping
     * Validates: Requirements 4.1
     * 
     * For any chapter list containing EPUB_DOWNLOAD chapters, they SHALL all be 
     * grouped under "下载链接"
     */
    "all EPUB_DOWNLOAD chapters are grouped under 下载链接".config(invocations = 10) {
        checkAll(arbChapterListWithDownloads()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Find all EPUB_DOWNLOAD chapters
            val downloadChapters = chapters.filter { chapter ->
                metadataMap[chapter.id]?.chapterType == ChapterType.EPUB_DOWNLOAD
            }
            
            if (downloadChapters.isNotEmpty()) {
                // There should be a group named "下载链接"
                val downloadGroup = groups.find { it.name == "下载链接" }
                downloadGroup shouldBe downloadGroup // Should not be null
                
                // All download chapters should be in this group
                downloadGroup?.let { group ->
                    group.chapters.size shouldBe downloadChapters.size
                    downloadChapters.forEach { chapter ->
                        group.chapters shouldContain chapter
                    }
                    
                    // The group should be collapsible
                    group.isCollapsible shouldBe true
                }
            }
        }
    }
    
    /**
     * Property: Download links group is collapsible
     * 
     * For any download links group, it SHALL be marked as collapsible
     */
    "download links group is collapsible".config(invocations = 10) {
        checkAll(arbChapterListWithDownloads().filter { (chapters, _) -> 
            chapters.isNotEmpty() 
        }) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            val downloadGroup = groups.find { it.name == "下载链接" }
            if (downloadGroup != null) {
                downloadGroup.isCollapsible shouldBe true
            }
        }
    }
    
    /**
     * Property: No EPUB_DOWNLOAD chapters outside download links group
     * 
     * For any chapter list, EPUB_DOWNLOAD chapters SHALL NOT appear in other groups
     */
    "no EPUB_DOWNLOAD chapters outside download links group".config(invocations = 10) {
        checkAll(arbChapterListWithDownloads()) { (chapters, metadataMap) ->
            val groups = mapChaptersToGroups(chapters, metadataMap)
            
            // Check all groups except "下载链接"
            groups.filter { it.name != "下载链接" }.forEach { group ->
                group.chapters.forEach { chapter ->
                    val chapterType = metadataMap[chapter.id]?.chapterType ?: ChapterType.NORMAL
                    chapterType shouldBe chapterType // Should not be EPUB_DOWNLOAD
                }
            }
        }
    }
})

/**
 * Arbitrary generator for chapter list with EPUB_DOWNLOAD chapters
 */
private fun arbChapterListWithDownloads(): Arb<Pair<List<MangaChapter>, Map<Long, ChapterMetadata>>> = arbitrary {
    val downloadCount = Arb.int(0..5).bind()
    val normalCount = Arb.int(0..10).bind()
    val epubInternalCount = Arb.int(0..10).bind()
    
    val chapters = mutableListOf<MangaChapter>()
    val metadataMap = mutableMapOf<Long, ChapterMetadata>()
    
    // Add EPUB_DOWNLOAD chapters
    repeat(downloadCount) {
        val chapterId = Arb.long(1L..1000000L).bind()
        val chapter = MangaChapter(
            id = chapterId,
            title = "Download ${Arb.string(5..20).bind()}",
            number = it.toFloat(),
            volume = 0,
            url = "https://example.com/download/${chapterId}.epub",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestMangaSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.EPUB_DOWNLOAD,
            parentChapterId = null,
            epubFileName = "volume_${it}.epub"
        )
    }
    
    // Add NORMAL chapters
    repeat(normalCount) {
        val chapterId = Arb.long(1000001L..2000000L).bind()
        val chapter = MangaChapter(
            id = chapterId,
            title = "Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "https://example.com/chapter/${chapterId}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestMangaSource
        )
        chapters.add(chapter)
        metadataMap[chapterId] = ChapterMetadata(
            chapterId = chapterId,
            chapterType = ChapterType.NORMAL,
            parentChapterId = null,
            epubFileName = null
        )
    }
    
    // Add EPUB_INTERNAL chapters
    repeat(epubInternalCount) {
        val chapterId = Arb.long(2000001L..3000000L).bind()
        val parentId = Arb.long(1L..1000000L).bind()
        val chapter = MangaChapter(
            id = chapterId,
            title = "Internal Chapter ${it + 1}",
            number = (it + 1).toFloat(),
            volume = 1,
            url = "file:///path/to/epub.epub#chapter/${it}",
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = TestMangaSource
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
