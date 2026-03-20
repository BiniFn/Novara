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
    details: Connect Mihon, Aniyomi, and Legado ecosystems, and track the planned TVBox integration work.
  - title: Reliable WebDAV Sync
    details: Set up free or self-hosted WebDAV storage for multi-device backup and synchronization.
---

## Start With The Right Page

- Start with [Getting Started](./getting-started.md) if you are new to the project.
- Read [Source Integrations](./source-integrations.md) if you need Mihon, Aniyomi, or Legado ecosystems.
- Read [Architecture Review](./architecture/architecture-review.md) if you want a project-level architectural assessment before changing major subsystems.
- Read [Architecture Roadmap](./architecture/architecture-roadmap.md) if you want to understand the active epics and planning.
- Read [UI Improvement](./architecture/ui_improvement.md) to track UI enhancements.
- Read [Automatic Translation](./automatic-translation.md) if you want local OCR + translation inside the reader.
- Read [Development](./development.md) and [Contributing](./contributing.md) if you want to build or modify the app.

## What Makes Kototoro Different

- One Android app for manga, novels, and video
- Local OCR + translation directly in the reader
- Video super-resolution support on supported pipelines
- WebDAV-based multi-device sync without vendor lock-in
- Broad source support across built-in parsers and external ecosystems

## Key External Source Repositories

These repositories matter because they are the most common entry points for real device setups.

- Mihon: [Keiyoushi Extensions](https://github.com/keiyoushi/extensions), [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions), [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20)
- Aniyomi: [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source), [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)
- Legado: [XIU2 Yuedu](https://github.com/XIU2/Yuedu)
