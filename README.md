# Kototoro — Manga Reader

[![Language: English](https://img.shields.io/badge/Language-English-blue)](README.md) [![中文](https://img.shields.io/badge/语言-中文-blue)](README.zh.md)

## Overview

Kototoro is an unofficial fork of Kotatsu focused on delivering a great manga reading experience, with enhanced support for Chinese-language sites while retaining full reader functionality.

### Highlights

- Full feature set from Kotatsu (reader, search, history, favorites, downloads)
- Optimized support for Chinese manga websites
- Foldable device support with adaptive layouts
- WebDAV-based auto backup and sync

## Tech Stack

- Kotlin, Android Jetpack
- Kotatsu Parser framework
- WebDAV for cloud sync
- Trae — AI IDE

## Installation

### Build from source

```bash
git clone https://github.com/skepsun/Kototoro.git
cd Kototoro
./gradlew assembleDebug
```

### Download APK

Grab the latest APK from the [Releases](https://github.com/skepsun/kototoro/releases) page.

## Usage

### Foldable devices

- Split-view reading (list and content side by side)
- Adaptive layout for different form factors

### WebDAV sync

1. Configure your WebDAV server in Settings
2. Auto-backup reading progress to the cloud
3. Sync state across multiple devices

## Development

### Requirements

- Android Studio 2022.3+
- JDK 17+
- Android SDK 33+

### Contributing

Issues and pull requests are welcome.

## License

This project follows the license of Kotatsu. See [LICENSE](LICENSE).

## Acknowledgements

- Kotatsu team — original reader and parser framework
- Venera team — feature-rich open-source reader with excellent Chinese parsers

## Contact

- GitHub Issues: https://github.com/skepsun/kototoro/issues
- Email: chuxiongsun@gmail.com

---

If you find this project helpful, please give it a ⭐.