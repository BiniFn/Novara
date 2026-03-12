# Reader Features

Kototoro is designed as an all-in-one Android reader instead of a single-purpose manga app. This page summarizes the core user-facing capabilities and explains where each one fits.

## Core Product Areas

### Manga, Novels, and Video in One App

Kototoro handles three content types in one place:

- Manga
- Novels
- Video

This keeps history, favorites, source access, and sync in one app instead of splitting the workflow across multiple readers.

### Local OCR + Translation

Kototoro includes local OCR + translation directly inside the reader. This is one of the most distinctive parts of the project because it keeps the workflow close to the page instead of sending users to external desktop tools.

Typical workflow:

- Detect text from the page
- Recognize it with the configured OCR pipeline
- Translate locally or through an API
- Render the translated result back into the reading view

Read more: [Automatic Translation](./automatic-translation.md)

### Video Playback and Super-Resolution

Kototoro includes built-in video playback and can also use video super-resolution pipelines on supported devices and configurations.

Typical capabilities include:

- Online playback
- Source-side episode browsing
- Resolution / stream selection when available
- Screen rotation and playback controls

### Sync and Portability

Kototoro uses WebDAV for multi-device backup and synchronization. This is intended for users who want device-independent sync without relying on a closed cloud service.

Read more: [WebDAV Sync](./webdav-sync.md)

### Broad Source Integration

Kototoro supports several source ecosystems:

- Built-in sources
- Mihon manga sources
- Aniyomi video sources
- Legado reading sources

This lets users keep broader catalogs in one app without being locked to one source format.

Read more: [Source Integrations](./source-integrations.md)

## By Content Type

### Manga Reading

Kototoro includes a full manga workflow with:

- Source browsing
- Library and favorites management
- Chapter reading
- Downloads for offline use
- Reader customization and layout options

### Novel Reading

Kototoro supports novel-oriented workflows including:

- Online novel sources
- Local novel reading
- Illustrated chapter handling
- EPUB-related flows where supported by the source and pipeline

### Video Consumption

Kototoro supports video source browsing and playback inside the same app, which is useful for users who want a single library and a single sync workflow.

## Related Documents

- [Documentation Hub](./README.md)
- [Getting Started](./getting-started.md)
- [Automatic Translation](./automatic-translation.md)
- [Source Integrations](./source-integrations.md)
- [WebDAV Sync](./webdav-sync.md)
- [FAQ](./faq.md)
- [Troubleshooting](./troubleshooting.md)
