package org.skepsun.kototoro.local.epub

/**
 * Sealed class representing different types of EPUB-related errors.
 * 
 * This provides type-safe error handling with user-friendly messages
 * for different error scenarios.
 * 
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
sealed class EpubError(
    val message: String,
    val userMessage: String,
    val cause: Throwable? = null
) {
    
    /**
     * EPUB parsing errors (Requirement 10.1, 10.2)
     */
    sealed class ParseError(message: String, userMessage: String, cause: Throwable? = null) : 
        EpubError(message, userMessage, cause) {
        
        class InvalidFormat(cause: Throwable? = null) : ParseError(
            message = "Invalid EPUB format",
            userMessage = "EPUB file format is invalid and cannot be parsed",
            cause = cause
        )
        
        class CorruptedFile(fileName: String, cause: Throwable? = null) : ParseError(
            message = "EPUB file is corrupted: $fileName",
            userMessage = "EPUB file is corrupted and cannot be read: $fileName",
            cause = cause
        )
        
        class MissingComponents(component: String, cause: Throwable? = null) : ParseError(
            message = "Missing required EPUB component: $component",
            userMessage = "EPUB file is missing required component: $component",
            cause = cause
        )
        
        class UnsupportedVersion(version: String, cause: Throwable? = null) : ParseError(
            message = "Unsupported EPUB version: $version",
            userMessage = "Unsupported EPUB version: $version",
            cause = cause
        )
        
        class MalformedHtml(chapterIndex: Int, cause: Throwable? = null) : ParseError(
            message = "Malformed HTML in chapter $chapterIndex",
            userMessage = "HTML format error in chapter $chapterIndex",
            cause = cause
        )
    }
    
    /**
     * File system errors (Requirement 10.4)
     */
    sealed class FileSystemError(message: String, userMessage: String, cause: Throwable? = null) : 
        EpubError(message, userMessage, cause) {
        
        class FileNotFound(fileName: String, cause: Throwable? = null) : FileSystemError(
            message = "EPUB file not found: $fileName",
            userMessage = "EPUB file not found, it may have been deleted: $fileName",
            cause = cause
        )
        
        class InsufficientStorage(requiredBytes: Long, availableBytes: Long) : FileSystemError(
            message = "Insufficient storage: required $requiredBytes bytes, available $availableBytes bytes",
            userMessage = "Insufficient storage space. Required: ${formatBytes(requiredBytes)}, Available: ${formatBytes(availableBytes)}"
        )
        
        class PermissionDenied(path: String, cause: Throwable? = null) : FileSystemError(
            message = "Permission denied: $path",
            userMessage = "No access permission: $path",
            cause = cause
        )
        
        class ReadError(fileName: String, cause: Throwable? = null) : FileSystemError(
            message = "Failed to read EPUB file: $fileName",
            userMessage = "Failed to read EPUB file: $fileName",
            cause = cause
        )
        
        class WriteError(fileName: String, cause: Throwable? = null) : FileSystemError(
            message = "Failed to write EPUB file: $fileName",
            userMessage = "Failed to write EPUB file: $fileName",
            cause = cause
        )
        
        companion object {
            private fun formatBytes(bytes: Long): String {
                return when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                    else -> "${bytes / (1024 * 1024 * 1024)} GB"
                }
            }
        }
    }
    
    /**
     * Chapter loading errors (Requirement 10.3)
     */
    sealed class ChapterLoadError(message: String, userMessage: String, cause: Throwable? = null) : 
        EpubError(message, userMessage, cause) {
        
        class InvalidChapterId(chapterId: Long) : ChapterLoadError(
            message = "Invalid chapter ID: $chapterId",
            userMessage = "Invalid chapter ID: $chapterId"
        )
        
        class ChapterNotFound(chapterId: Long) : ChapterLoadError(
            message = "Chapter not found: $chapterId",
            userMessage = "Chapter does not exist: $chapterId"
        )
        
        class IndexOutOfBounds(index: Int, totalChapters: Int) : ChapterLoadError(
            message = "Chapter index $index out of bounds (total: $totalChapters)",
            userMessage = "Chapter index out of range: $index (total $totalChapters chapters)"
        )
        
        class InvalidUrl(url: String) : ChapterLoadError(
            message = "Invalid chapter URL format: $url",
            userMessage = "Invalid chapter URL format: $url"
        )
        
        class LoadFailed(chapterId: Long, cause: Throwable? = null) : ChapterLoadError(
            message = "Failed to load chapter: $chapterId",
            userMessage = "Failed to load chapter: $chapterId",
            cause = cause
        )
    }
    
    /**
     * Database errors
     */
    sealed class DatabaseError(message: String, userMessage: String, cause: Throwable? = null) : 
        EpubError(message, userMessage, cause) {
        
        class MappingInsertFailed(chapterId: Long, cause: Throwable? = null) : DatabaseError(
            message = "Failed to insert chapter mapping: $chapterId",
            userMessage = "Failed to save chapter mapping: $chapterId",
            cause = cause
        )
        
        class QueryFailed(query: String, cause: Throwable? = null) : DatabaseError(
            message = "Database query failed: $query",
            userMessage = "Database query failed",
            cause = cause
        )
        
        class MigrationFailed(fromVersion: Int, toVersion: Int, cause: Throwable? = null) : DatabaseError(
            message = "Database migration failed: $fromVersion -> $toVersion",
            userMessage = "Database upgrade failed: version $fromVersion -> $toVersion",
            cause = cause
        )
    }
    
    /**
     * Generic error for unexpected situations
     */
    class UnexpectedError(message: String, cause: Throwable? = null) : EpubError(
        message = message,
        userMessage = "An unknown error occurred: $message",
        cause = cause
    )
}

/**
 * Extension function to convert exceptions to EpubError
 */
fun Throwable.toEpubError(): EpubError {
    return when (this) {
        is java.io.FileNotFoundException -> EpubError.FileSystemError.FileNotFound(
            fileName = message ?: "unknown",
            cause = this
        )
        is java.io.IOException -> EpubError.FileSystemError.ReadError(
            fileName = message ?: "unknown",
            cause = this
        )
        is SecurityException -> EpubError.FileSystemError.PermissionDenied(
            path = message ?: "unknown",
            cause = this
        )
        else -> EpubError.UnexpectedError(
            message = message ?: "Unknown error",
            cause = this
        )
    }
}
