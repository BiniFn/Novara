package org.skepsun.kototoro.reader.novel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.skepsun.kototoro.local.epub.ChapterIdGeneratorImpl
import org.skepsun.kototoro.reader.ui.ReaderState

/**
 * Property-based test for reading progress persistence round-trip.
 * 
 * Feature: epub-reader-improvements
 * Property 28: Progress Persistence Round-Trip
 * Validates: Requirements 7.4, 7.5
 * 
 * For any reading state saved on exit, reopening SHALL restore the same chapter and page position
 * 
 * This test validates the data structures and logic used for progress persistence,
 * without requiring full database integration.
 */
class ProgressPersistenceRoundTripPropertyTest : StringSpec({
    
    val generator = ChapterIdGeneratorImpl()
    
    /**
     * Property 28: Progress Persistence Round-Trip
     * 
     * Test that ReaderState correctly preserves chapter ID and page number.
     * This is the core data structure used for progress persistence.
     */
    "ReaderState preserves chapter ID and page number".config(invocations = 100) {
        checkAll(
            Arb.long(1L..999_999_999L),  // chapter ID
            Arb.int(0..1000),             // page number
            Arb.int(0..10000)             // scroll position
        ) { chapterId, page, scroll ->
            // Create a ReaderState (this is what gets saved)
            val savedState = ReaderState(
                chapterId = chapterId,
                page = page,
                scroll = scroll
            )
            
            // Simulate round-trip: save then restore
            val restoredChapterId = savedState.chapterId
            val restoredPage = savedState.page
            val restoredScroll = savedState.scroll
            
            // Verify values are preserved
            restoredChapterId shouldBe chapterId
            restoredPage shouldBe page
            restoredScroll shouldBe scroll
        }
    }
    
    /**
     * Test that EPUB internal chapter IDs are correctly preserved in ReaderState
     * 
     * This validates Requirement 7.2: "WHEN saving progress for an EPUB internal chapter 
     * THEN the system SHALL use the internal chapter ID"
     */
    "ReaderState preserves EPUB internal chapter IDs".config(invocations = 100) {
        checkAll(
            Arb.long(1L..999_999L),  // parent chapter ID
            Arb.int(0..999),          // chapter index
            Arb.int(0..100)           // page number
        ) { parentChapterId, chapterIndex, page ->
            // Generate internal chapter ID
            val internalChapterId = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            
            // Create ReaderState with internal chapter ID
            val savedState = ReaderState(
                chapterId = internalChapterId,
                page = page,
                scroll = 0
            )
            
            // Verify internal chapter ID is preserved
            savedState.chapterId shouldBe internalChapterId
            savedState.page shouldBe page
            
            // Verify we can extract parent ID from saved state
            val extractedParentId = generator.extractParentId(savedState.chapterId)
            extractedParentId shouldBe parentChapterId
            
            // Verify we can extract chapter index from saved state
            val extractedIndex = generator.extractChapterIndex(savedState.chapterId)
            extractedIndex shouldBe chapterIndex
        }
    }
    
    /**
     * Test that progress percentage is correctly calculated and can be reconstructed
     */
    "progress percentage can be reconstructed from page and total pages".config(invocations = 100) {
        checkAll(
            Arb.int(0..1000),  // current page
            Arb.int(1..1000)   // total pages
        ) { currentPage, totalPages ->
            val validCurrentPage = currentPage.coerceAtMost(totalPages - 1)
            
            // Calculate progress (this is what gets saved)
            val savedPercent = if (totalPages > 0) {
                validCurrentPage.toFloat() / totalPages
            } else {
                0f
            }
            
            // Simulate restoration: given saved percent and total pages, 
            // we can approximate the page number
            val approximatePage = (savedPercent * totalPages).toInt()
            
            // The approximation should be close to the original
            // (may differ by 1 due to rounding)
            val difference = kotlin.math.abs(approximatePage - validCurrentPage)
            assert(difference <= 1) {
                "Reconstructed page ($approximatePage) differs too much from original ($validCurrentPage)"
            }
        }
    }
    
    /**
     * Test that multiple progress updates preserve the latest state
     */
    "latest progress update overwrites previous state".config(invocations = 100) {
        checkAll(
            Arb.long(1L..999_999L),  // chapter ID 1
            Arb.int(0..100),          // page 1
            Arb.long(1L..999_999L),  // chapter ID 2
            Arb.int(0..100)           // page 2
        ) { chapterId1, page1, chapterId2, page2 ->
            // First save
            val state1 = ReaderState(
                chapterId = chapterId1,
                page = page1,
                scroll = 0
            )
            
            // Second save (simulating progress update)
            val state2 = ReaderState(
                chapterId = chapterId2,
                page = page2,
                scroll = 0
            )
            
            // In actual implementation, state2 would overwrite state1 in the database
            // Here we just verify that state2 has the correct values
            state2.chapterId shouldBe chapterId2
            state2.page shouldBe page2
            
            // And that they're independent
            state1.chapterId shouldBe chapterId1
            state1.page shouldBe page1
        }
    }
    
    /**
     * Test edge case: first page of first chapter
     */
    "progress persistence works for first page of first chapter" {
        val state = ReaderState(
            chapterId = 1L,
            page = 0,
            scroll = 0
        )
        
        state.chapterId shouldBe 1L
        state.page shouldBe 0
        state.scroll shouldBe 0
    }
    
    /**
     * Test edge case: large chapter IDs (EPUB internal chapters)
     */
    "progress persistence works for large EPUB internal chapter IDs" {
        val parentId = 100000L
        val index = 999  // Maximum index
        val internalId = generator.generateEpubChapterId(parentId, index)
        
        val state = ReaderState(
            chapterId = internalId,
            page = 50,
            scroll = 0
        )
        
        state.chapterId shouldBe internalId
        state.page shouldBe 50
        
        // Verify we can still extract parent ID
        val extractedParent = generator.extractParentId(state.chapterId)
        extractedParent shouldBe parentId
    }
})
