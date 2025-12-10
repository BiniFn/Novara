# JSON Source Import Feature

This package contains the UI implementation for importing JSON source configurations into Kototoro.

## Components

### ImportJsonViewModel
- Manages the UI state for the import dialog
- Handles file selection and JSON content import
- Communicates with `JsonSourceManager` to perform the actual import
- Provides state flow for UI updates (Idle, Loading, Success, Error)

### ImportJsonDialogFragment
- Dialog UI for importing JSON sources
- Provides two import methods:
  1. **File Selection**: User can select a JSON file from device storage
  2. **Text Paste**: User can paste JSON content directly into a text field
- Supports source type selection (Legado or TVBox)
- Displays loading state, success messages, and error details
- Automatically dismisses after successful import

## Usage

### Opening the Import Dialog

From a Fragment:
```kotlin
val dialog = ImportJsonDialogFragment.newInstance()
dialog.show(childFragmentManager, ImportJsonDialogFragment.TAG)
```

From an Activity:
```kotlin
val dialog = ImportJsonDialogFragment.newInstance()
dialog.show(supportFragmentManager, ImportJsonDialogFragment.TAG)
```

### Integration Point

The import dialog is integrated into the Sources Settings screen:
- Navigate to: Settings → Manga Sources → Import JSON Sources
- This opens the import dialog where users can select a file or paste JSON content

## Supported Formats

### Legado Book Sources
The import feature supports Legado book source JSON format. Example:
```json
[
  {
    "bookSourceName": "Example Source",
    "bookSourceUrl": "https://example.com",
    "bookSourceType": 0,
    "enabled": true,
    "searchUrl": "https://example.com/search?q={{key}}",
    "ruleSearch": {
      "bookList": "div.book-list > div.item",
      "name": "h3.title@text",
      "author": "span.author@text",
      "coverUrl": "img@src",
      "bookUrl": "a@href"
    },
    "ruleBookInfo": {
      "name": "h1.book-title@text",
      "author": "div.author@text",
      "coverUrl": "img.cover@src",
      "intro": "div.intro@text"
    },
    "ruleToc": {
      "chapterList": "div.chapter-list > a",
      "chapterName": "@text",
      "chapterUrl": "@href"
    },
    "ruleContent": {
      "content": "div.content@html"
    }
  }
]
```

### TVBox Site Configurations
TVBox support is planned for future implementation.

## Error Handling

The import dialog handles various error scenarios:
- **Invalid JSON Format**: Shows specific error message about JSON parsing failure
- **Missing Required Fields**: Displays which fields are missing from the configuration
- **Network Errors**: Shows network-related error messages (if applicable)
- **Validation Errors**: Displays validation errors for invalid URLs or configurations

## Validation

Before import, the system validates:
1. JSON format is valid
2. Required fields are present (`bookSourceName`, `bookSourceUrl`)
3. URL format is valid (must be http/https)
4. Source identifiers are unique

## Import Process

1. User selects file or pastes JSON content
2. User selects source type (Legado/TVBox)
3. User clicks "Import" button
4. System validates JSON format and content
5. System generates unique identifiers for each source
6. System saves valid sources to database
7. Dialog shows success message with count of imported sources
8. Dialog automatically dismisses after 1.5 seconds

## State Management

The ViewModel manages four UI states:
- **Idle**: Initial state, ready for user input
- **Loading**: Import in progress, UI disabled
- **Success**: Import completed successfully, shows count
- **Error**: Import failed, shows error message

## Localization

The feature supports multiple languages:
- English (default)
- Chinese (Simplified)

All user-facing strings are localized in:
- `res/values/strings.xml`
- `res/values-zh/strings.xml`

## Future Enhancements

- TVBox source import support
- Batch import from multiple files
- Import history tracking
- Source preview before import
- Import from URL
- Export functionality
