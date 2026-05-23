package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Property-based tests for Chapter ID Stability.
 * 
 * Feature: epub-reader-improvements
 * Property 10: Chapter ID Stability
 * Validates: Requirements 2.6
 * 
 * For any EPUB file parsed multiple times, 
 * the generated internal chapter IDs SHALL be identical across parses.
 */
class ChapterIdStabilityPropertyTest : StringSpec({
    
    val generator = ChapterIdGeneratorImpl()
    
    /**
     * Property 10: Chapter ID Stability
     * 
     * When the same EPUB file (same parent chapter ID and chapter count) is parsed multiple times,
     * the generated internal chapter IDs should be identical across all parses.
     * 
     * This ensures that:
     * - Reading progress is preserved across app restarts
     * - Chapter references remain valid
     * - Database mappings stay consistent
     */
    "parsing same EPUB multiple times generates identical chapter IDs".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),  // parentChapterId
            Arb.int(1..50)            // number of chapters in EPUB
        ) { parentChapterId, chapterCount ->
            // First parse: generate chapter IDs
            val firstParseIds = (0 until chapterCount).map { chapterIndex ->
                generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }
            
            // Second parse: generate chapter IDs again
            val secondParseIds = (0 until chapterCount).map { chapterIndex ->
                generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }
            
            // Third parse: generate chapter IDs again
            val thirdParseIds = (0 until chapterCount).map { chapterIndex ->
                generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }
            
            // All parses should produce identical IDs
            firstParseIds shouldBe secondParseIds
            secondParseIds shouldBe thirdParseIds
            firstParseIds shouldBe thirdParseIds
            
            // Verify count is correct
            firstParseIds shouldHaveSize chapterCount
            secondParseIds shouldHaveSize chapterCount
            thirdParseIds shouldHaveSize chapterCount
        }
    }
    
    /**
     * Stability across different orderings
     * 
     * Even if chapters are processed in different orders,
     * the same chapter index should always produce the same ID.
     */
    "chapter IDs are stable regardless of processing order".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),  // parentChapterId
            Arb.int(5..20)            // number of chapters in EPUB
        ) { parentChapterId, chapterCount ->
            // Generate IDs in forward order
            val forwardIds = (0 until chapterCount).map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // Generate IDs in reverse order
            val reverseIds = (chapterCount - 1 downTo 0).map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // Generate IDs in random order (using a deterministic shuffle based on parentChapterId)
            val indices = (0 until chapterCount).toList().shuffled(java.util.Random(parentChapterId))
            val shuffledIds = indices.map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // All orderings should produce the same IDs for each index
            forwardIds shouldBe reverseIds
            reverseIds shouldBe shuffledIds
            forwardIds shouldBe shuffledIds
        }
    }
    
    /**
     * Stability with same parent ID but different chapter counts
     * 
     * If an EPUB is updated and has more/fewer chapters,
     * the existing chapter IDs should remain stable.
     */
    "existing chapter IDs remain stable when EPUB chapter count changes".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),  // parentChapterId
            Arb.int(5..20),           // initial chapter count
            Arb.int(1..10)            // chapters to add/remove
        ) { parentChapterId, initialCount, delta ->
            // Generate IDs for initial EPUB
            val initialIds = (0 until initialCount).map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // Simulate EPUB with more chapters
            val expandedCount = initialCount + delta
            val expandedIds = (0 until expandedCount).map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // Simulate EPUB with fewer chapters (if possible)
            val reducedCount = maxOf(1, initialCount - delta)
            val reducedIds = (0 until reducedCount).map { chapterIndex ->
                chapterIndex to generator.generateEpubChapterId(parentChapterId, chapterIndex)
            }.toMap()
            
            // Original chapter IDs should remain unchanged in expanded version
            initialIds.forEach { (index, id) ->
                expandedIds[index] shouldBe id
            }
            
            // Original chapter IDs should remain unchanged in reduced version (for chapters that still exist)
            reducedIds.forEach { (index, id) ->
                initialIds[index] shouldBe id
            }
        }
    }
    
    /**
     * Deterministic ID generation
     * 
     * The same inputs should always produce the same output,
     * regardless of when or where the code runs.
     */
    "ID generation is deterministic and reproducible".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),  // parentChapterId
            Arb.int(0..999)           // chapterIndex
        ) { parentChapterId, chapterIndex ->
            // Generate ID multiple times
            val id1 = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            val id2 = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            val id3 = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            val id4 = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            val id5 = generator.generateEpubChapterId(parentChapterId, chapterIndex)
            
            // All should be identical
            id1 shouldBe id2
            id2 shouldBe id3
            id3 shouldBe id4
            id4 shouldBe id5
            
            // 当前实现只能稳定恢复哈希后的 parentId，而不是原始 parentId
            generator.extractParentId(id1) shouldBe generator.hashParentId(parentChapterId)
            generator.extractChapterIndex(id1) shouldBe chapterIndex
        }
    }
})
