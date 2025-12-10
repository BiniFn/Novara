# Rule Engine

This package contains the rule engine implementation for parsing HTML content using CSS selectors and regular expressions.

## Overview

The rule engine is a core component of the JSON source system that interprets and executes selector rules defined in JSON configurations (such as Legado book sources) to extract data from HTML documents.

## Components

### RuleEngine Interface

The main interface that defines the contract for parsing HTML content:

- `parseField(element, rule)`: Parse a single field from an HTML element
- `parseList(document, listRule, itemRules)`: Parse a list of items from a document
- `compileRule(rule)`: Compile a rule string for efficient reuse

### DefaultRuleEngine

The default implementation that supports:

- **CSS Selectors**: Standard CSS selector syntax with attribute extraction
  - Examples: `div.title`, `a@href`, `img@src`, `p@text`
  - Supported attributes: text, html, outerHtml, src, href, alt, title, class, id, data, ownText
  
- **Regular Expressions**: Pattern matching with capture group support
  - Format: `@regex:pattern`
  - Example: `@regex:<title>(.*?)</title>`
  - Includes ReDoS protection (pattern length limit, dangerous pattern detection)

### RuleCache

LRU cache for compiled rules to improve performance:

- Default cache size: 200 rules
- Provides cache statistics (hit rate, miss count, etc.)
- Thread-safe implementation using Android's LruCache

### CompiledRule

Data class representing a compiled rule:

- `type`: Rule type (CSS, REGEX, XPATH, JSON_PATH)
- `selector`: The selector/pattern string
- `attribute`: Optional attribute to extract (for CSS rules)
- `regex`: Compiled regex pattern (for REGEX rules)

### RuleType

Enum defining supported rule types:

- `CSS`: CSS selector rules
- `REGEX`: Regular expression rules
- `XPATH`: XPath rules (future support)
- `JSON_PATH`: JSONPath rules (future support)

## Usage Example

```kotlin
// Create rule engine with cache
val ruleEngine = DefaultRuleEngine()

// Parse a single field
val html = """<div class="title">My Book</div>"""
val doc = Jsoup.parse(html)
val title = ruleEngine.parseField(doc, "div.title@text")
// Result: "My Book"

// Parse a list
val listHtml = """
    <div class="book-item">
        <h3 class="title">Book 1</h3>
        <a href="/book1">Read</a>
    </div>
    <div class="book-item">
        <h3 class="title">Book 2</h3>
        <a href="/book2">Read</a>
    </div>
"""
val listDoc = Jsoup.parse(listHtml)
val itemRules = mapOf(
    "title" to "h3.title@text",
    "url" to "a@href"
)
val books = ruleEngine.parseList(listDoc, "div.book-item", itemRules)
// Result: [{"title": "Book 1", "url": "/book1"}, {"title": "Book 2", "url": "/book2"}]

// Use regex
val regexHtml = """<div>Author: <span>John Doe</span></div>"""
val regexDoc = Jsoup.parse(regexHtml)
val author = ruleEngine.parseField(regexDoc, "@regex:Author: <span>(.*?)</span>")
// Result: "John Doe"
```

## Error Handling

The rule engine is designed to be resilient:

- Invalid selectors return empty strings instead of crashing
- Blank rules return empty results
- Regex complexity is validated to prevent ReDoS attacks
- All exceptions are caught and logged

## Performance

- Rules are compiled once and cached for reuse
- Cache hit rate is typically > 80% for repeated rule usage
- LRU eviction ensures memory efficiency
- Supports monitoring via cache statistics

## Testing

Comprehensive unit tests are provided in:

- `RuleEngineTest.kt`: Tests for CSS selectors, regex, list parsing, and error handling
- `RuleCacheTest.kt`: Tests for cache behavior, hits/misses, and eviction

## Future Enhancements

- XPath support for more complex document traversal
- JSONPath support for parsing JSON responses
- Custom attribute extractors
- Rule composition and chaining
