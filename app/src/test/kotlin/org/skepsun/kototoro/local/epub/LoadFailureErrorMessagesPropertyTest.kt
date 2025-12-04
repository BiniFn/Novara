package org.skepsun.kototoro.local.epub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for chapter load failure error messages.
 * 
 * Feature: epub-reader-improvements, Property 35: Load Failure Error Messages
 * Validates: Requirements 10.3
 * 
 * Property: For any chapter load failure, an error message indicating the cause SHALL be shown
 */
class LoadFailureErrorMessagesPropertyTest : StringSpec({
    
    "all chapter load errors have non-empty user messages" {
        checkAll(100, arbChapterLoadError()) { error ->
            error.userMessage.shouldNotBeBlank()
        }
    }
    
    "all chapter load errors have non-empty technical messages" {
        checkAll(100, arbChapterLoadError()) { error ->
            error.message.shouldNotBeBlank()
        }
    }
    
    "chapter load error user messages are in Chinese" {
        checkAll(100, arbChapterLoadError()) { error ->
            // User messages should contain Chinese characters or be localized
            val hasChinese = error.userMessage.any { it.code in 0x4E00..0x9FFF }
            hasChinese shouldBe true
        }
    }
    
    "invalid chapter ID errors contain the chapter ID" {
        checkAll(Arb.long(1L..1000000L)) { chapterId ->
            val error = EpubError.ChapterLoadError.InvalidChapterId(chapterId)
            error.userMessage shouldContain chapterId.toString()
            error.message shouldContain chapterId.toString()
        }
    }
    
    "chapter not found errors contain the chapter ID" {
        checkAll(Arb.long(1L..1000000L)) { chapterId ->
            val error = EpubError.ChapterLoadError.ChapterNotFound(chapterId)
            error.userMessage shouldContain chapterId.toString()
            error.message shouldContain chapterId.toString()
        }
    }
    
    "index out of bounds errors contain index and total" {
        checkAll(100, Arb.int(0..999), Arb.int(1..100)) { index, total ->
            val error = EpubError.ChapterLoadError.IndexOutOfBounds(index, total)
            error.userMessage shouldContain index.toString()
            error.userMessage shouldContain total.toString()
            error.message shouldContain index.toString()
            error.message shouldContain total.toString()
        }
    }
    
    "invalid URL errors contain the URL" {
        checkAll(100, Arb.string(1..100)) { url ->
            val error = EpubError.ChapterLoadError.InvalidUrl(url)
            error.userMessage shouldContain url
            error.message shouldContain url
        }
    }
    
    "load failed errors contain chapter ID" {
        checkAll(Arb.long(1L..1000000L)) { chapterId ->
            val error = EpubError.ChapterLoadError.LoadFailed(chapterId)
            error.userMessage shouldContain chapterId.toString()
            error.message shouldContain chapterId.toString()
        }
    }
    
    "chapter load errors preserve cause exceptions" {
        checkAll(Arb.long(1L..1000000L), Arb.string(1..50)) { chapterId, message ->
            val cause = Exception(message)
            val error = EpubError.ChapterLoadError.LoadFailed(chapterId, cause)
            error.cause shouldBe cause
        }
    }
    
    "file system errors have descriptive user messages" {
        checkAll(100, arbFileSystemError()) { error ->
            error.userMessage.shouldNotBeBlank()
            // Should indicate the type of problem
            val hasKeyword = error.userMessage.contains("未找到") ||
                            error.userMessage.contains("空间不足") ||
                            error.userMessage.contains("权限") ||
                            error.userMessage.contains("失败")
            hasKeyword shouldBe true
        }
    }
    
    "file not found errors contain filename" {
        checkAll(100, Arb.string(1..50)) { fileName ->
            val error = EpubError.FileSystemError.FileNotFound(fileName)
            error.userMessage shouldContain fileName
            error.message shouldContain fileName
        }
    }
    
    "insufficient storage errors contain size information" {
        checkAll(Arb.long(1L..1000000000L), Arb.long(1L..1000000000L)) { required, available ->
            val error = EpubError.FileSystemError.InsufficientStorage(required, available)
            // Should contain some indication of sizes
            error.userMessage.shouldNotBeBlank()
            error.message shouldContain required.toString()
            error.message shouldContain available.toString()
        }
    }
})

/**
 * Arbitrary generator for chapter load errors
 */
private fun arbChapterLoadError(): Arb<EpubError.ChapterLoadError> = arbitrary {
    Arb.choice(
        arbitrary { 
            EpubError.ChapterLoadError.InvalidChapterId(
                Arb.long(1L..1000000L).bind()
            ) 
        },
        arbitrary { 
            EpubError.ChapterLoadError.ChapterNotFound(
                Arb.long(1L..1000000L).bind()
            ) 
        },
        arbitrary { 
            EpubError.ChapterLoadError.IndexOutOfBounds(
                Arb.int(0..999).bind(),
                Arb.int(1..100).bind()
            ) 
        },
        arbitrary { 
            EpubError.ChapterLoadError.InvalidUrl(
                Arb.string(1..100).bind()
            ) 
        },
        arbitrary { 
            EpubError.ChapterLoadError.LoadFailed(
                Arb.long(1L..1000000L).bind()
            ) 
        }
    ).bind()
}

/**
 * Arbitrary generator for file system errors
 */
private fun arbFileSystemError(): Arb<EpubError.FileSystemError> = arbitrary {
    Arb.choice(
        arbitrary { 
            EpubError.FileSystemError.FileNotFound(
                Arb.string(1..50).bind()
            ) 
        },
        arbitrary { 
            EpubError.FileSystemError.InsufficientStorage(
                Arb.long(1L..1000000000L).bind(),
                Arb.long(1L..1000000000L).bind()
            ) 
        },
        arbitrary { 
            EpubError.FileSystemError.PermissionDenied(
                Arb.string(1..100).bind()
            ) 
        },
        arbitrary { 
            EpubError.FileSystemError.ReadError(
                Arb.string(1..50).bind()
            ) 
        },
        arbitrary { 
            EpubError.FileSystemError.WriteError(
                Arb.string(1..50).bind()
            ) 
        }
    ).bind()
}
