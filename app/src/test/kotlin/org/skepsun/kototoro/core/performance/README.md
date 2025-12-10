# Performance Tests

This directory contains performance tests for the JSON source system.

## Test Files

### RuleCachePerformanceTest
Tests the effectiveness of rule compilation caching:
- Cache hit rate with repeated rules
- Performance improvement from caching
- Cache behavior with many rules (stress test)
- Cache prewarming effectiveness
- Cache statistics accuracy
- Concurrent cache access

**Validates**: Requirements 11.3 (Performance - Rule caching)

### JsonSourceLoadingPerformanceTest
Tests JSON source loading performance:
- Loading 100+ sources efficiently
- Concurrent source import performance
- Cache effectiveness with repeated queries
- Batch operations performance
- Memory usage with many sources

**Validates**: Requirements 11.1, 11.2 (Performance - Source loading)

### ConcurrentParsingPerformanceTest
Tests concurrent parsing and parser pool:
- Parser pool lazy loading
- Parser reuse from pool
- Concurrent parser access
- Parser invalidation
- Parser pool with many sources
- Rule engine cache with concurrent parsing
- Memory usage with parser pool

**Validates**: Requirements 11.4, 11.5 (Performance - Concurrent operations)

## Running the Tests

```bash
# Run all performance tests
./gradlew :app:testDebugUnitTest --tests "*PerformanceTest"

# Run specific test
./gradlew :app:testDebugUnitTest --tests "RuleCachePerformanceTest"
```

## Performance Benchmarks

Expected performance characteristics:

### Rule Cache
- Hit rate: >90% for repeated rules
- Cache speedup: 2-10x depending on rule complexity
- Max cache size: 500 rules
- Concurrent access: Thread-safe with no performance degradation

### Source Loading
- 150 sources: <1 second
- 50 source import: <5 seconds
- Cached queries: 10-100x faster than DB queries
- Batch operations: Single DB call for multiple operations

### Parser Pool
- Lazy loading: Parsers created on-demand
- Parser reuse: Same instance returned for same source
- 100 parsers: <2 seconds creation time
- Cached access: Near-instant (<100ms for 100 parsers)
- Memory usage: <20MB for 50 parsers

### Memory Usage
- 200 sources: <50MB
- 50 parsers: <20MB
- Rule cache: Minimal overhead (compiled rules are lightweight)

## Optimization Strategies

### 1. Rule Compilation Cache
- Increased cache size from 200 to 500 rules
- Added cache prewarming for common rules
- Implemented cache hit rate monitoring
- Thread-safe concurrent access

### 2. Async Loading
- Concurrent source processing with coroutines
- Lazy parser creation (only when needed)
- Parser pool for instance reuse
- Background thread loading

### 3. Database Optimization
- Added indexes on frequently queried columns
- Batch operations for multiple sources
- Query result caching
- Composite indexes for complex queries

## Notes

- Performance tests use mocking to isolate components
- Actual performance may vary based on device and data
- Tests include both unit-level and integration-level scenarios
- Memory measurements are approximate and device-dependent
