# EPUB Data Models

This package contains the data models and types for EPUB support in the Kototoro application.

## Overview

The EPUB data models extend the existing `MangaChapter` model from the parsers library to support EPUB-specific functionality without modifying the shared library code.

## Components

### ChapterType Enum

Defines three types of chapters:
- `NORMAL`: Regular online chapters from manga sources
- `EPUB_DOWNLOAD`: EPUB download link chapters (parent chapters)
- `EPUB_INTERNAL`: Chapters extracted from within EPUB files

### ChapterMetadata

Holds EPUB-specific metadata that cannot be stored in the parsers library's `MangaChapter`:
- `chapterId`: The chapter ID this metadata belongs to
- `chapterType`: Type classification (NORMAL, EPUB_DOWNLOAD, or EPUB_INTERNAL)
- `parentChapterId`: ID of the parent EPUB download chapter (for EPUB_INTERNAL chapters)
- `epubFileName`: Display name of the EPUB file (for grouping)

### ChapterWithMetadata

A wrapper class that combines `MangaChapter` with `ChapterMetadata` for convenient access to both the chapter data and EPUB-specific metadata.

### EpubContent and EpubChapter

Data classes for representing parsed EPUB content:
- `EpubContent`: Contains title, author, and list of chapters
- `EpubChapter`: Contains index, title, and text content of a single chapter

## Usage

### Creating Chapter Metadata

```kotlin
// For a normal chapter
val normalMetadata = chapter.toMetadata(chapterType = ChapterType.NORMAL)

// For an EPUB download chapter
val downloadMetadata = chapter.toMetadata(
    chapterType = ChapterType.EPUB_DOWNLOAD,
    epubFileName = "volume1.epub"
)

// For an EPUB internal chapter
val internalMetadata = chapter.toMetadata(
    chapterType = ChapterType.EPUB_INTERNAL,
    parentChapterId = 12345L,
    epubFileName = "volume1.epub"
)
```

### Working with ChapterWithMetadata

```kotlin
// Create a chapter with metadata
val chapterWithMeta = chapter.withMetadata(
    chapterType = ChapterType.EPUB_INTERNAL,
    parentChapterId = 12345L,
    epubFileName = "volume1.epub"
)

// Access properties
when (chapterWithMeta.chapterType) {
    ChapterType.NORMAL -> handleNormalChapter(chapterWithMeta.chapter)
    ChapterType.EPUB_DOWNLOAD -> handleDownloadChapter(chapterWithMeta.chapter)
    ChapterType.EPUB_INTERNAL -> handleInternalChapter(
        chapterWithMeta.chapter,
        chapterWithMeta.parentChapterId!!
    )
}
```

## Design Rationale

### Why Not Modify MangaChapter Directly?

The `MangaChapter` class is part of the `kototoro-parsers` library, which is:
1. A shared library used across multiple components
2. Focused on parsing manga sources, not local EPUB handling
3. Should remain independent of app-specific features

### Metadata Storage Strategy

The `ChapterMetadata` is designed to be stored separately from `MangaChapter` instances:
- In memory: Use a `Map<Long, ChapterMetadata>` keyed by chapter ID
- In database: Use the `EpubChapterMappingEntity` table
- At runtime: Combine using `ChapterWithMetadata` wrapper

This approach provides:
- Clean separation of concerns
- No modifications to shared library code
- Flexibility to add more EPUB-specific fields in the future
- Easy migration path if the parsers library adds native EPUB support

## Testing

Property-based tests verify:
- Every chapter has exactly one type (NORMAL, EPUB_DOWNLOAD, or EPUB_INTERNAL)
- EPUB_INTERNAL chapters always have a parent chapter ID
- EPUB_INTERNAL chapters always have an EPUB filename
- NORMAL chapters don't have parent chapter IDs
- EPUB_DOWNLOAD chapters don't have parent chapter IDs

See `ChapterTypeClassificationPropertyTest.kt` for implementation details.
