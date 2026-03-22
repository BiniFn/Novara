# Getting Started

This is the best entry point for new users. The goal is simple: get the app installed, connect the sources you need, and enable optional features only when they matter to your workflow.

## What Kototoro Is Good At

Kototoro is built around five practical strengths:

1. One Android app for manga, novels, and video.
2. Local OCR + translation directly inside the reader.
3. Video super-resolution support.
4. Reliable multi-device sync through WebDAV.
5. Broad source compatibility through built-in and external ecosystems.

## 10-Minute Setup

1. Install the latest APK from [Releases](https://github.com/skepsun/kototoro/releases).
2. Open the app and confirm which workflow you need first: manga, novel, or video.
3. Set up your sources.
4. If you use more than one device, configure WebDAV before you build a large library.
5. If you want in-reader translation, enable it and download the required models.

## Choose Your Path

### I want manga sources

1. Open Kototoro and review the built-in sources first.
2. If you also use Mihon, install the Mihon extensions you need there.
3. Return to Kototoro and refresh source detection if the new sources do not appear immediately.

Common Mihon extension repositories:

- [Keiyoushi Extensions](https://github.com/keiyoushi/extensions)
- [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions)
- [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20)

Read more: [Source Integrations](./source-integrations.md)

### I want video sources

1. Choose whether your video workflow is extension-based or JSON-based.
2. For Aniyomi-style sources, install the video extensions you need and let Kototoro detect the installed APKs.
3. For TVBox-style sources, import a JSON file or JSON URL from `Settings -> Content Sources -> Import JSON Sources`.
4. Open Kototoro and check whether the new sources appear in `Browse -> Content Sources`.
5. Configure playback options only after you confirm basic playback works.

Common Aniyomi extension repositories:

- [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source)
- [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)

Read more: [Reader Features](./reader-features.md) and [Source Integrations](./source-integrations.md)

### I want novels

1. Start with built-in reading sources.
2. If your workflow depends on Legado, prepare the JSON file or JSON URL first.
3. Import it from `Settings -> Content Sources -> Import JSON Sources`.
4. Check the imported entries in `Settings -> Content Sources -> JSON Sources Directory`.
5. Test chapter loading and formatting before importing a large reading list.

Common Legado source repository:

- [XIU2 Yuedu](https://github.com/XIU2/Yuedu)

Read more: [Source Integrations](./source-integrations.md)

## Optional Setup

### Automatic Translation

1. Open `Settings -> Reader -> Translation`.
2. Enable translation.
3. Pick the OCR engine you want to try first.
4. Open `Manage models` and download the models you need.
5. Set source language, target language, and translation mode.

Read more: [Automatic Translation](./automatic-translation.md)

### WebDAV Sync

1. Open `Settings -> Backup & Restore`.
2. Enter your WebDAV endpoint, username, and password.
3. Run a manual backup.
4. Restore from the same location on your second device.

Read more: [WebDAV Sync](./webdav-sync.md)

## Favorites Import and Site Sync

Kototoro can import favorites from supported logged-in sites or push local favorites back to them.

| Site | Import Favorites | Sync Favorites | Notes |
| :--- | :---: | :---: | :--- |
| CopyManga | ✅ | ✅ | Login required |
| Zaimanhua | ✅ | ✅ | Login required |
| Komiic | ✅ | ✅ | Login required |
| Baozi Manga | ✅ | ✅ | Login required |
| Manhuagui | ✅ | ✅ | Login required |
| Hentai Manga | ✅ | ✅ | Login required |
| Pica Manga | ✅ | ✅ | Login required |

Basic flow:

1. Open `Favorites`.
2. Use the top-right menu.
3. Choose `Import from Site` or `Sync to Site`.
4. Select the target site and continue.

## Next Documents

- [Documentation Hub](./README.md)
- [Reader Features](./reader-features.md)
- [Automatic Translation](./automatic-translation.md)
- [Source Integrations](./source-integrations.md)
- [WebDAV Sync](./webdav-sync.md)
- [FAQ](./faq.md)
- [Troubleshooting](./troubleshooting.md)
