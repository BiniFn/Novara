# Source Integrations

Kototoro supports multiple source ecosystems in addition to its own built-in parsers. This page explains what each integration is for and how to approach setup without unnecessary complexity.

## Overview

The app can work with:

- Built-in sources
- Mihon manga sources
- Aniyomi video / anime sources
- Legado reading sources

This broadens the available catalog without forcing users into a single source format or a single companion app.

## Key Repositories

These repositories are important because they are the practical entry point for many external-source workflows.

### Mihon / Tachiyomi-style manga repositories

- [Keiyoushi Extensions](https://github.com/keiyoushi/extensions)
- [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions)
- [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20) for Chinese-site coverage

### Aniyomi video repositories

- [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source)
- [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)

### Legado reading repositories

- [XIU2 Yuedu](https://github.com/XIU2/Yuedu)

## Mihon Sources

Mihon integration is aimed at manga sources and is usually the first external setup for users who already have a Mihon extension library.

### How It Works

- Install Mihon
- Install the source extensions you need in Mihon
- Kototoro detects compatible extensions installed on the device

Common repositories:

- [Keiyoushi Extensions](https://github.com/keiyoushi/extensions)
- [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions)
- [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20)

### Best Use Cases

- Manga-heavy setups
- Users already maintaining a Mihon extension library

## Aniyomi Sources

Aniyomi integration is aimed at video-oriented sources.

### How It Works

- Install Aniyomi
- Install the desired video-oriented extensions
- Kototoro detects compatible installed extensions

Common repositories:

- [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source)
- [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)

### Best Use Cases

- Users who want video sources inside the same app as reading workflows

## Legado Sources

Legado integration is aimed at reading / novel source ecosystems.

### How It Fits

- Useful for novel-oriented workflows
- Useful when your reading setup already depends on Legado source definitions

Common repository:

- [XIU2 Yuedu](https://github.com/XIU2/Yuedu)

### Best Use Cases

- Novel-oriented workflows
- Users who already maintain Legado source definitions

## Setup Flow

1. Install the relevant external app ecosystem if needed.
2. Install or import the desired source definitions or extensions there.
3. Open Kototoro.
4. Go to `Browse` or the related source management area in settings.
5. Refresh source detection if newly added sources do not appear immediately.

## Expectations and Limits

- Source availability depends on what is installed or imported on the device.
- Compatibility can vary by extension version, source implementation, and upstream site changes.
- External ecosystems expand access, but they also inherit external breakage when websites or extension APIs change.

## Related Documents

- [Documentation Hub](./README.md)
- [Getting Started](./getting-started.md)
- [Mihon Integration Reference](./reference/mihon-integration.md)
- [TVBox Integration Implementation Plan](./architecture/tvbox-integration-implementation-plan.md)
- [Reader Features](./reader-features.md)
- [FAQ](./faq.md)
- [Troubleshooting](./troubleshooting.md)
