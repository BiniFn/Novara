# EPUB Reader Property-Based Tests

This directory contains property-based tests for the EPUB reader implementation using Kotest.

## Why Android Instrumented Tests?

These tests are located in `androidTest` rather than `test` because:

1. **EpubLib Dependency**: The `me.ag2s.epublib` library uses Android-specific classes like `android.util.Log` and `org.xmlpull.v1.XmlSerializer`
2. **EPUB Creation**: Creating test EPUB files requires the `EpubWriter` class which needs Android XML serialization
3. **Real Environment**: Testing EPUB parsing benefits from running in a real Android environment

## Running the Tests

### Using Android Studio
1. Right-click on the test class or package
2. Select "Run Tests"

### Using Gradle
```bash
./gradlew connectedDebugAndroidTest
```

### Running Specific Test Classes
```bash
./gradlew connectedDebugAndroidTest --tests "org.skepsun.kototoro.local.epub.SpineExtractionPropertyTest"
```

## Test Coverage

The property-based tests cover:

1. **SpineExtractionPropertyTest** - Property 5: Complete Spine Extraction
   - Validates that exactly N chapters are extracted from EPUB with N spine references
   - Validates sequential chapter indices

2. **TitlePreservationPropertyTest** - Property 6: Title Preservation
   - Validates that chapter titles from EPUB metadata are preserved exactly
   - Tests special characters and various title formats

3. **DefaultTitleGenerationPropertyTest** - Property 7: Default Title Generation
   - Validates default title generation for chapters without titles
   - Tests empty, whitespace, and mixed title scenarios

4. **HtmlToTextConversionPropertyTest** - Property 8: HTML to Text Conversion
   - Validates HTML tag removal
   - Tests script/style tag removal
   - Tests HTML entity decoding
   - Tests whitespace normalization

## Test Configuration

- Each test runs 100 iterations by default (configurable via `.config(invocations = N)`)
- Tests use Kotest's property-based testing framework
- Test EPUB files are created in the app's cache directory and cleaned up after tests

## Implementation Notes

The `EpubReaderImpl` class includes a `logError()` helper function that safely handles Android logging, allowing the code to work in both production and test environments.
