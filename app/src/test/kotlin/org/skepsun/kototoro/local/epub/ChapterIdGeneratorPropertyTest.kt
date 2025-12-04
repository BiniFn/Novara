package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Property-based tests for ChapterIdGenerator.
 * 
 * Feature: epub-reader-improvements
 * 
 * Note: Tests updated to work with hash-based ID generation that supports
 * parent chapter IDs of any size (including very large IDs like 7925123592942842239).
 */
class ChapterIdGeneratorPropertyTest : StringSpec({
    
    val generator = ChapterIdGeneratorImpl()
    
    /**
     * Property 16: Chapter ID Generation Formula (Updated for Hash-Based Approach)
     * Validates: Requirements 5.1
     * 
     * For any parent chapter ID P and chapter index I, 
     * the generated ID SHALL equal hashParentId(P) + (I * 1000000) + 1
     * 
     * This test now uses the full range of Long values to ensure the hash-based
     * approach works with real-world chapter IDs.
     */
    "generated ID follows hash-based formula for all inputs".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),  // Test with full range of Long values
            Arb.int(0..999)
        ) { parentId, index ->
            val generatedId = generator.generateEpubChapterId(parentId, index)
            val hashedParentId = generator.hashParentId(parentId)
            val expectedId = hashedParentId + (index * 1_000_000L) + 1
            generatedId shouldBe expectedId
        }
    }
    
    /**
     * Test with specific real-world chapter ID that was failing before
     */
    "works with real-world large chapter ID from Novelia".config(invocations = 1) {
        val realWorldParentId = 7925123592942842239L
        val index = 0
        
        // Should not throw exception
        val generatedId = generator.generateEpubChapterId(realWorldParentId, index)
        
        // Should be able to extract chapter index
        val extractedIndex = generator.extractChapterIndex(generatedId)
        extractedIndex shouldBe index
        
        // Should be able to extract hashed parent ID
        val extractedHashedParentId = generator.extractParentId(generatedId)
        val expectedHashedParentId = generator.hashParentId(realWorldParentId)
        extractedHashedParentId shouldBe expectedHashedParentId
    }
    
    "extractParentId returns correct hashed parent ID".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999)
        ) { parentId, index ->
            val internalId = generator.generateEpubChapterId(parentId, index)
            val extractedHashedParentId = generator.extractParentId(internalId)
            val expectedHashedParentId = generator.hashParentId(parentId)
            extractedHashedParentId shouldBe expectedHashedParentId
        }
    }
    
    "extractChapterIndex returns correct chapter index".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999)
        ) { parentId, index ->
            val internalId = generator.generateEpubChapterId(parentId, index)
            val extractedIndex = generator.extractChapterIndex(internalId)
            extractedIndex shouldBe index
        }
    }
    
    "round trip: generate then extract returns hashed parent ID and original index".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999)
        ) { parentId, index ->
            val internalId = generator.generateEpubChapterId(parentId, index)
            val extractedHashedParentId = generator.extractParentId(internalId)
            val extractedIndex = generator.extractChapterIndex(internalId)
            val expectedHashedParentId = generator.hashParentId(parentId)
            
            extractedHashedParentId shouldBe expectedHashedParentId
            extractedIndex shouldBe index
        }
    }
    
    "hash function is deterministic".config(invocations = 100) {
        checkAll(Arb.long(1L..Long.MAX_VALUE)) { parentId ->
            val hash1 = generator.hashParentId(parentId)
            val hash2 = generator.hashParentId(parentId)
            hash1 shouldBe hash2
        }
    }
    
    "hash function produces values less than 1,000,000".config(invocations = 100) {
        checkAll(Arb.long(1L..Long.MAX_VALUE)) { parentId ->
            val hash = generator.hashParentId(parentId)
            assert(hash >= 0 && hash < 1_000_000) {
                "Hash $hash for parent ID $parentId is not in range [0, 1000000)"
            }
        }
    }
})
