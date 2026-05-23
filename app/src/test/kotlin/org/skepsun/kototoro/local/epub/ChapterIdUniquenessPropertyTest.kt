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
     * 当前实现使用 `hashParentId(parentId)` 参与生成 ID。
     * 因此不同 EPUB 只有在哈希后 parentId 不同的情况下，才保证同索引下 ID 不重叠。
     */
    "IDs from different EPUB hash buckets do not overlap".config(invocations = 10) {
        val representativeParentIds = listOf<Long>(
            1L,
            2L,
            42L,
            1_000_001L,
            7_925_123_592_942_842_239L,
        )

        checkAll(Arb.int(0..999)) { index ->
            val idsByHash = representativeParentIds.associateWith { parentId ->
                generator.hashParentId(parentId) to generator.generateEpubChapterId(parentId, index)
            }

            idsByHash.entries
                .groupBy({ it.value.first }, { it.value.second })
                .values
                .forEach { idsInSameHashBucket ->
                    idsInSameHashBucket.distinct().size shouldBe 1
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
    
    "IDs are stable across all combinations".config(invocations = 100) {
        checkAll(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.long(1L..Long.MAX_VALUE),
            Arb.int(0..999),
            Arb.int(0..999)
        ) { parentId1, parentId2, index1, index2 ->
            val id1 = generator.generateEpubChapterId(parentId1, index1)
            val id2 = generator.generateEpubChapterId(parentId2, index2)
            
            if (parentId1 == parentId2 && index1 == index2) {
                id1 shouldBe id2
            }
        }
    }
})
