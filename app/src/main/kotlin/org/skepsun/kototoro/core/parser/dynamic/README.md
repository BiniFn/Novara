# Dynamic Parser Implementation

This package contains the implementation of dynamic parsers for JSON-based manga sources.

## Components

### 1. DynamicParserFactory
**Location**: `DynamicParserFactory.kt`

Factory class responsible for creating dynamic parser instances from JSON configurations.

**Key Methods**:
- `createParser(source, context)`: Creates a parser instance based on the source type
- `validateConfig(config)`: Validates JSON configuration before creating a parser

**Supported Types**:
- Legado book sources (implemented)
- TVBox sources (planned)

### 2. DynamicLegadoParser
**Location**: `DynamicLegadoParser.kt`

Runtime parser implementation for Legado book source configurations.

**Features**:
- Parses search results using `ruleSearch`
- Extracts book details using `ruleBookInfo`
- Parses chapter lists using `ruleToc`
- Extracts chapter content using `ruleContent`
- Comprehensive error handling with detailed logging
- Converts relative URLs to absolute URLs
- Generates HTML pages for novel content

**Key Methods**:
- `getListPage()`: Fetches and parses manga/novel list
- `getDetails()`: Fetches and parses manga/novel details
- `getPages()`: Fetches and parses chapter content

### 3. ParserPool
**Location**: `ParserPool.kt`

Thread-safe pool for caching parser instances to improve performance.

**Features**:
- Uses `ConcurrentHashMap` for thread-safe access
- Caches parsers by source ID
- Supports invalidation when configurations change
- Provides pool management methods (clear, size, contains)

**Key Methods**:
- `getOrCreate(sourceId, factory)`: Gets cached parser or creates new one
- `invalidate(sourceId)`: Removes parser from cache
- `clear()`: Clears all cached parsers

### 4. DynamicParserLogger
**Location**: `DynamicParserLogger.kt`

Structured logging utility for dynamic parser operations.

**Features**:
- Logs errors with context (source ID, URL, rule)
- Separate methods for different error types
- Supports debug, info, warning, and error levels

**Key Methods**:
- `logListPageError()`: Logs list parsing errors
- `logDetailsError()`: Logs details parsing errors
- `logChapterError()`: Logs chapter parsing errors
- `logContentError()`: Logs content parsing errors

### 5. ValidationResult
**Location**: `../jsonsource/ValidationResult.kt`

Data class for validation results.

**Features**:
- Indicates success or failure
- Contains list of error messages
- Provides factory methods for common cases

## Usage Example

```kotlin
// Create factory
val ruleEngine = DefaultRuleEngine(RuleCache())
val factory = DynamicParserFactory(ruleEngine)

// Create parser from JSON source
val source = JsonSourceEntity(
    id = "JSON_LEGADO_EXAMPLE",
    name = "Example Source",
    type = JsonSourceType.LEGADO,
    config = legadoJsonConfig,
    enabled = true,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis(),
)

val context = getMangaLoaderContext()
val parser = factory.createParser(source, context)

// Use parser
val results = parser?.getListPage(1, SortOrder.UPDATED, MangaListFilter())
```

## Error Handling

All parser methods implement comprehensive error handling:

1. **Network Errors**: Caught and logged, return empty results
2. **Parsing Errors**: Caught and logged, return empty results or error pages
3. **Rule Errors**: Caught and logged with rule details
4. **Invalid URLs**: Handled gracefully with error messages

The parser never crashes the application - it always returns safe fallback values.

## Testing

Integration tests are located in:
- `test/kotlin/org/skepsun/kototoro/core/parser/dynamic/DynamicParserIntegrationTest.kt`

Tests cover:
- Parser factory creation
- Configuration validation
- Parser pool operations
- Error handling
- Parser lifecycle

## Performance Considerations

1. **Parser Pooling**: Parsers are cached to avoid recreation overhead
2. **Rule Caching**: Rules are compiled and cached by the RuleEngine
3. **Lazy Loading**: Parsers are only created when first used
4. **Thread Safety**: ParserPool uses ConcurrentHashMap for safe concurrent access

## Future Enhancements

1. Support for TVBox video sources
2. Support for custom rule types (XPath, JSONPath)
3. Advanced URL template processing
4. Parser configuration hot-reloading
5. Performance metrics and monitoring
