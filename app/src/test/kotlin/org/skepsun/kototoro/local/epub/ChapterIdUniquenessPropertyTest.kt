package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Property-based tests for Chapter ID uniqueness across different EPUBs.
 * 
 * Feature: epub-reader-improvements
 */
class ChapterIdUniquenessPropertyTest : StringSpec({
    
    val generator = ChapterIdGeneratorImpl()
    
    /**
     * Property 17: Cross-EPUB ID Uniqueness
     * Validates: Requirements 5.2
     * 
     * For any two different EPUB files (different parent IDs),
     * their internal chapter IDs SHALL NOT overlap
     */
    "IDs from different EPUBs do not overlap".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.long(1L..Long.MAX_VALUE).filter { it != 0L }, // Ensure different parent IDs
            Arb.int(0..999)
        ) { parentId1, offset, index ->
            // Ensure parentId2 is different from parentId1 and within valid range
            val parentId2 = if (parentId1 + offset < 1_000_000) {
                parentId1 + offset
            } else {
                (parentId1 - offset).coerceAtLeast(1L)
            }
            
            if (parentId1 != parentId2) {
                val id1 = generator.generateEpubChapterId(parentId1, index)
                val id2 = generator.generateEpubChapterId(parentId2, index)
                
                // IDs should be different when parent IDs are different
                id1 shouldNotBe id2
            }
        }
    }
    
    "IDs within same EPUB are unique for different indices".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999),
            Arb.int(0..999)
        ) { parentId, index1, index2 ->
            val id1 = generator.generateEpubChapterId(parentId, index1)
            val id2 = generator.generateEpubChapterId(parentId, index2)
            
            // IDs should be different when indices are different
            if (index1 != index2) {
                id1 shouldNotBe id2
            }
        }
    }
    
    "IDs are unique across all combinations".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999),
            Arb.int(0..999)
        ) { parentId1, parentId2, index1, index2 ->
            val id1 = generator.generateEpubChapterId(parentId1, index1)
            val id2 = generator.generateEpubChapterId(parentId2, index2)
            
            // IDs should only be equal if both parent ID and index are the same
            if (parentId1 == parentId2 && index1 == index2) {
                // Same inputs should produce same ID
                id1 shouldBe id2
            } else {
                // Different inputs should produce different IDs
                id1 shouldNotBe id2
            }
        }
    }
})
