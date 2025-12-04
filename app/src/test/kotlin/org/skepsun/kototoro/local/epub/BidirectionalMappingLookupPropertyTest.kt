package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.skepsun.kototoro.core.db.entity.EpubChapterMappingEntity

/**
 * Property-based tests for bidirectional EPUB chapter mapping lookup.
 * 
 * Feature: epub-reader-improvements
 * Property 20: Bidirectional Mapping Lookup
 * Validates: Requirements 5.5
 * 
 * For any stored chapter mapping, it SHALL be retrievable by both 
 * internal chapter ID and parent chapter ID.
 * 
 * This unit test version uses an in-memory map to simulate database operations
 * without requiring an Android device or emulator.
 */
class BidirectionalMappingLookupPropertyTest : StringSpec({
    
    /**
     * In-memory storage simulating the database
     */
    class InMemoryMappingStore {
        private val mappings = mutableMapOf<Long, EpubChapterMappingEntity>()
        
        fun insert(mapping: EpubChapterMappingEntity) {
            mappings[mapping.internalChapterId] = mapping
        }
        
        fun insertAll(mappingList: List<EpubChapterMappingEntity>) {
            mappingList.forEach { insert(it) }
        }
        
        fun getById(internalChapterId: Long): EpubChapterMappingEntity? {
            return mappings[internalChapterId]
        }
        
        fun getByParentId(parentChapterId: Long): List<EpubChapterMappingEntity> {
            return mappings.values
                .filter { it.parentChapterId == parentChapterId }
                .sortedBy { it.chapterIndex }
        }
        
        fun clear() {
            mappings.clear()
        }
    }
    
    /**
     * Generates a random EpubChapterMappingEntity for testing.
     */
    fun generateMapping(parentId: Long, chapterIndex: Int): EpubChapterMappingEntity {
        val internalId = parentId + (chapterIndex * 1_000_000L) + 1
        
        return EpubChapterMappingEntity(
            internalChapterId = internalId,
            parentChapterId = parentId,
            epubFilePath = "/storage/emulated/0/epub/chapter_${parentId}_vol1.epub",
            epubFileName = "Volume 1",
            chapterIndex = chapterIndex,
            chapterTitle = "Chapter ${chapterIndex + 1}",
            createdAt = System.currentTimeMillis()
        )
    }
    
    "mapping can be retrieved by internal chapter ID".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(0..999)
        ) { parentId, chapterIndex ->
            store.clear()
            val mapping = generateMapping(parentId, chapterIndex)
            
            // Insert the mapping
            store.insert(mapping)
            
            // Retrieve by internal chapter ID
            val retrieved = store.getById(mapping.internalChapterId)
            
            // Verify mapping was found
            retrieved shouldNotBe null
            retrieved shouldBe mapping
        }
    }
    
    "mapping can be retrieved by parent chapter ID".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(1..20)
        ) { parentId, chapterCount ->
            store.clear()
            val mappings = (0 until chapterCount).map { i ->
                generateMapping(parentId, i)
            }
            
            // Insert all mappings
            store.insertAll(mappings)
            
            // Retrieve by parent ID
            val retrieved = store.getByParentId(parentId)
            
            // Verify all mappings were found
            retrieved shouldHaveSize chapterCount
            
            // Verify each mapping is present
            mappings.forEach { original ->
                retrieved shouldContain original
            }
        }
    }
    
    "bidirectional lookup returns consistent data".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(1..20)
        ) { parentId, chapterCount ->
            store.clear()
            val mappings = (0 until chapterCount).map { i ->
                generateMapping(parentId, i)
            }
            
            // Insert all mappings
            store.insertAll(mappings)
            
            // For each mapping, verify it can be retrieved both ways
            mappings.forEach { original ->
                // Retrieve by internal chapter ID
                val byInternalId = store.getById(original.internalChapterId)
                byInternalId shouldNotBe null
                
                // Retrieve by parent chapter ID
                val byParentId = store.getByParentId(original.parentChapterId)
                val fromParentLookup = byParentId.find { it.internalChapterId == original.internalChapterId }
                
                // Verify both lookups return the same data
                byInternalId shouldBe fromParentLookup
                byInternalId shouldBe original
            }
        }
    }
    
    "parent ID lookup returns all child mappings".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(5..30)
        ) { parentId, chapterCount ->
            store.clear()
            val expectedInternalIds = mutableSetOf<Long>()
            
            // Generate and insert multiple chapters
            for (i in 0 until chapterCount) {
                val mapping = generateMapping(parentId, i)
                expectedInternalIds.add(mapping.internalChapterId)
                store.insert(mapping)
            }
            
            // Retrieve by parent ID
            val retrieved = store.getByParentId(parentId)
            val retrievedInternalIds = retrieved.map { it.internalChapterId }.toSet()
            
            // Verify all expected IDs are present
            retrievedInternalIds shouldBe expectedInternalIds
        }
    }
    
    "internal ID lookup returns correct parent ID".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(0..999)
        ) { parentId, chapterIndex ->
            store.clear()
            val mapping = generateMapping(parentId, chapterIndex)
            
            // Insert the mapping
            store.insert(mapping)
            
            // Retrieve by internal chapter ID
            val retrieved = store.getById(mapping.internalChapterId)
            
            // Verify parent ID is correct
            retrieved shouldNotBe null
            retrieved!!.parentChapterId shouldBe parentId
            
            // Verify this mapping is also in the parent's children
            val siblings = store.getByParentId(parentId)
            siblings shouldContain mapping
        }
    }
    
    "parent ID lookup returns mappings sorted by chapter index".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.int(5..20)
        ) { parentId, chapterCount ->
            store.clear()
            
            // Insert chapters in random order
            val indices = (0 until chapterCount).shuffled()
            indices.forEach { i ->
                val mapping = generateMapping(parentId, i)
                store.insert(mapping)
            }
            
            // Retrieve by parent ID
            val retrieved = store.getByParentId(parentId)
            
            // Verify they are sorted by chapter index
            retrieved shouldHaveSize chapterCount
            retrieved.forEachIndexed { idx, mapping ->
                mapping.chapterIndex shouldBe idx
            }
        }
    }
    
    "multiple EPUBs have independent mappings".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L),
            Arb.long(1L..999_999L),
            Arb.int(1..10),
            Arb.int(1..10)
        ) { parentId1, parentId2, count1, count2 ->
            // Ensure different parent IDs
            if (parentId1 == parentId2) return@checkAll
            
            store.clear()
            
            // Insert mappings for first EPUB
            val mappings1 = (0 until count1).map { i ->
                generateMapping(parentId1, i)
            }
            store.insertAll(mappings1)
            
            // Insert mappings for second EPUB
            val mappings2 = (0 until count2).map { i ->
                generateMapping(parentId2, i)
            }
            store.insertAll(mappings2)
            
            // Retrieve each EPUB's mappings
            val retrieved1 = store.getByParentId(parentId1)
            val retrieved2 = store.getByParentId(parentId2)
            
            // Verify correct counts
            retrieved1 shouldHaveSize count1
            retrieved2 shouldHaveSize count2
            
            // Verify no cross-contamination
            retrieved1.forEach { it.parentChapterId shouldBe parentId1 }
            retrieved2.forEach { it.parentChapterId shouldBe parentId2 }
        }
    }
    
    "lookup by non-existent internal ID returns null".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L)
        ) { nonExistentId ->
            store.clear()
            
            // Try to retrieve non-existent mapping
            val retrieved = store.getById(nonExistentId)
            
            // Should return null
            retrieved shouldBe null
        }
    }
    
    "lookup by non-existent parent ID returns empty list".config(invocations = 10) {
        val store = InMemoryMappingStore()
        
        checkAll(
            Arb.long(1L..999_999L)
        ) { nonExistentParentId ->
            store.clear()
            
            // Try to retrieve mappings for non-existent parent
            val retrieved = store.getByParentId(nonExistentParentId)
            
            // Should return empty list
            retrieved shouldHaveSize 0
        }
    }
})
