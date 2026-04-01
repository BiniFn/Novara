---
layout: home

hero:
  name: Kototoro Docs
  text: One Android app for manga, novels, and video
  tagline: Setup guides, source integration references, OCR translation notes, sync workflows, and contributor documentation for Kototoro.
  image:
    src: /icon.png
    alt: Kototoro
  actions:
    - theme: brand
      text: Get Started
      link: /getting-started
    - theme: alt
      text: Set Up Sources
      link: /source-integrations
    - theme: alt
      text: GitHub
      link: https://github.com/skepsun/Kototoro

features:
  - title: All-in-One Reader
    details: Understand how Kototoro keeps manga, novels, video, history, favorites, and sync in one Android workflow.
  - title: Local OCR + Translation
    details: Configure on-device OCR, model downloads, translation modes, and practical fallback paths inside the reader.
  - title: External Source Ecosystems
    details: Connect Mihon, Aniyomi, IReader, Legado, and TVBox sources from the same in-app source management flow.
  - title: Tracking & Discovery
    details: Discover anime and manga from MAL, Kitsu, AniList, Bangumi, Shikimori, and MangaUpdates directly inside the app.
  - title: Video Player
    details: Built-in player with DLNA casting, subtitle and audio track selection, Anime4K super-resolution, and seek gestures.
  - title: Reliable WebDAV Sync
    details: Set up free or self-hosted WebDAV storage for multi-device backup and synchronization.
  - title: Fast OTA Updates
    details: Save bandwidth with pure-Kotlin bspatch incremental delta updates direct from GitHub Releases.
---

## Start With The Right Page

> [!IMPORTANT]
> **Disclaimer:** Kototoro does not bundle, host, or distribute any content sources, media, or copyrighted material. The application is a generic reader and player framework. All content sources are user-provided through third-party extensions, local file imports, or self-configured endpoints. The developers assume no responsibility for content accessed through user-installed sources.

- Start with [Getting Started](./getting-started.md) if you are new to the project.
- Read [Source Integrations](./source-integrations.md) if you need Mihon, Aniyomi, IReader, Legado, or TVBox ecosystems.
- Read [External Extension Integration Guide](./architecture/external-extension-integration-guide.md) if you are a developer looking to integrate Mihon/Tachiyomi extensions into your own app.
- Read [Architecture Review](./architecture/architecture-review.md) if you want a project-level architectural assessment before changing major subsystems.
- Read [Architecture Roadmap](./architecture/architecture-roadmap.md) if you want to understand the active epics and planning.
- Read [Incremental OTA Updates](./architecture/incremental-updates.md) to learn how Kototoro achieves NDK-free pure Kotlin bspatch delta updating.
- Read [Dynamic Plugin System Architecture](./architecture/dynamic_plugin_system.md) to understand how Kototoro and Kotatsu extension JARs are loaded dynamically using zero-overhead ClassLoaders.
- Read [Plugin Development Guide](./plugin_development_guide.md) to learn how to build and publish third-party plugins in an independent repository using GitHub Actions.
- Read [UI Improvement](./architecture/ui_improvement.md) to track UI enhancements.
- Read [Automatic Translation](./automatic-translation.md) if you want local OCR + translation inside the reader.
- Read [Development](./development.md) and [Contributing](./contributing.md) if you want to build or modify the app.

## What Makes Kototoro Different

- One Android app for manga, novels, and video
- Local OCR + translation directly in the reader
- Video super-resolution (Anime4K), DLNA casting, subtitle and audio track selection
- Tracking discovery across MAL, Kitsu, AniList, Bangumi, Shikimori, and MangaUpdates
- Broad source support: built-in, Kotatsu-Redo, Mihon, Aniyomi, IReader, Legado, TVBox
- Dynamic zero-overhead UI plugins via external classloaders
- Fast pure-Kotlin OTA delta updates

## Key External Source Repositories

These repositories matter because they are the most common entry points for real device setups.

- Mihon: [Keiyoushi Extensions](https://github.com/keiyoushi/extensions), [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions), [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20)
- Aniyomi: [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source), [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)
- Legado: [XIU2 Yuedu](https://github.com/XIU2/Yuedu)
