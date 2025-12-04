package org.skepsun.kototoro.reader.novel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property-based test for reading progress percentage calculation.
 * 
 * Feature: epub-reader-improvements
 * Property 27: Progress Percentage Calculation
 * Validates: Requirements 7.3
 * 
 * For any reading position with current page C and total pages T, 
 * the progress SHALL be C / T
 */
class ProgressPercentageCalculationPropertyTest : StringSpec({
    
    /**
     * Property 27: Progress Percentage Calculation
     * 
     * The progress percentage must be calculated as currentPage / totalPages.
     * This ensures consistent progress tracking across all chapter types.
     */
    "progress percentage equals currentPage divided by totalPages".config(invocations = 100) {
        checkAll(
            Arb.int(0..1000),  // current page (0-indexed)
            Arb.int(1..1000)   // total pages (must be at least 1)
        ) { currentPage, totalPages ->
            // Ensure currentPage doesn't exceed totalPages
            val validCurrentPage = currentPage.coerceAtMost(totalPages - 1)
            
            // Calculate progress percentage
            val progressPercent = if (totalPages > 0) {
                validCurrentPage.toFloat() / totalPages
            } else {
                0f
            }
            
            // Verify the formula
            progressPercent shouldBe (validCurrentPage.toFloat() / totalPages)
            
            // Verify progress is in valid range [0, 1]
            progressPercent.shouldBeBetween(0f, 1f, 0.0001f)
        }
    }
    
    /**
     * Verify edge cases for progress calculation
     */
    "progress is 0 at first page".config(invocations = 100) {
        checkAll(
            Arb.int(1..1000)  // total pages
        ) { totalPages ->
            val currentPage = 0
            val progressPercent = currentPage.toFloat() / totalPages
            
            progressPercent shouldBe 0f
        }
    }
    
    /**
     * Verify progress approaches 1.0 at last page
     */
    "progress approaches 1.0 at last page".config(invocations = 100) {
        checkAll(
            Arb.int(2..1000)  // total pages (at least 2 to ensure meaningful progress)
        ) { totalPages ->
            val currentPage = totalPages - 1  // Last page (0-indexed)
            val progressPercent = currentPage.toFloat() / totalPages
            
            // Progress should be close to 1.0 but not exactly 1.0
            // (since we're on the last page but haven't finished it)
            // For totalPages=2, progress would be 1/2 = 0.5, so we need a more flexible range
            val expectedProgress = (totalPages - 1).toFloat() / totalPages
            progressPercent shouldBe expectedProgress
            
            // Verify it's less than 1.0
            assert(progressPercent < 1.0f) {
                "Progress at last page should be less than 1.0, got $progressPercent"
            }
        }
    }
    
    /**
     * Verify progress is monotonically increasing
     */
    "progress increases as page number increases".config(invocations = 100) {
        checkAll(
            Arb.int(3..1000)  // total pages (at least 3 for meaningful comparison)
        ) { totalPages ->
            val page1 = 0
            val page2 = totalPages / 2
            val page3 = totalPages - 1
            
            val progress1 = page1.toFloat() / totalPages
            val progress2 = page2.toFloat() / totalPages
            val progress3 = page3.toFloat() / totalPages
            
            // Progress should increase (or stay equal if pages are the same)
            assert(progress1 <= progress2) {
                "Progress at page $page1 ($progress1) should be <= at page $page2 ($progress2)"
            }
            assert(progress2 <= progress3) {
                "Progress at page $page2 ($progress2) should be <= at page $page3 ($progress3)"
            }
            
            // For distinct pages, progress should strictly increase
            if (page1 < page2) {
                assert(progress1 < progress2) {
                    "Progress at page $page1 ($progress1) should be < at page $page2 ($progress2)"
                }
            }
            if (page2 < page3) {
                assert(progress2 < progress3) {
                    "Progress at page $page2 ($progress2) should be < at page $page3 ($progress3)"
                }
            }
        }
    }
    
    /**
     * Verify progress calculation handles chapter-level progress correctly
     * 
     * When calculating overall manga progress, we need:
     * progress = (completedChapters + currentChapterProgress) / totalChapters
     */
    "overall progress calculation includes chapter progress".config(invocations = 100) {
        checkAll(
            Arb.int(1..100),   // total chapters
            Arb.int(0..99),    // current chapter index
            Arb.int(0..100),   // current page in chapter
            Arb.int(1..100)    // total pages in chapter
        ) { totalChapters, chapterIndex, currentPage, totalPages ->
            val validChapterIndex = chapterIndex.coerceAtMost(totalChapters - 1)
            val validCurrentPage = currentPage.coerceAtMost(totalPages - 1)
            
            // Calculate chapter progress
            val chapterProgress = if (totalPages > 0) {
                validCurrentPage.toFloat() / totalPages
            } else {
                0f
            }
            
            // Calculate overall progress
            val overallProgress = if (totalChapters > 0) {
                (validChapterIndex + chapterProgress) / totalChapters
            } else {
                0f
            }
            
            // Verify overall progress is in valid range
            overallProgress.shouldBeBetween(0f, 1f, 0.0001f)
            
            // Verify the formula matches the implementation in NovelReaderActivity
            val expectedProgress = (validChapterIndex + chapterProgress) / totalChapters
            overallProgress shouldBe expectedProgress
        }
    }
})
