# Kototoro — Manga/Video/Novel Reader

![App Icon](metadata/en-US/icon.png)

[![English](https://img.shields.io/badge/Language-English-blue)](README_en.md) [![中文](https://img.shields.io/badge/语言-中文-blue)](README.md)

## Overview

Kototoro is an unofficial fork of Kotatsu focused on delivering a great manga, video, and novel reading experience, with enhanced support for Chinese-language sites while retaining full reader functionality.

### 🎯 Key Features

#### 🌟 Core Features
- ✅ **Chinese Site Support** - Optimized for Chinese manga/novel websites
- ✅ **Full Kotatsu Feature Set** - Complete manga reader functionality
- 🎬 **Video Site Support** - Built-in video player with quality selection
- 📚 **Novel Reading Support** - Support for online novels and EPUB downloads
- 📱 **Foldable Device Support** - Perfect adaptation for foldable devices with split-screen display
- ☁️ **WebDAV Auto Sync** - Automatic backup and sync across devices with smart merge


#### Known Issues
- Video playback and progress saving are unstable
- EPUB novels can be downloaded and read, but chapter navigation and progress saving have issues

## Screenshots
<div align="center">
    <img src="./metadata/en-US/images/tabletScreenshots/1.jpg" alt="All sources" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/2.jpg" alt="Comic list view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/3.jpg" alt="Video list view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/4.jpg" alt="Novel list view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/5.jpg" alt="Comic reading view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/6.jpg" alt="Video playing view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/7.jpg" alt="Novel reading view" width="300"/>
    <img src="./metadata/en-US/images/tabletScreenshots/8.jpg" alt="WebDAV setting view" width="300"/>
</div>

## Tech Stack

- **Kotlin** - Primary development language
- **Android Jetpack** - Modern Android development architecture
- **Kotatsu Parser** - Manga/novel parser framework
- **WebDAV** - Cloud sync protocol
- **Trae** - AI IDE for development assistance

## Installation

### Option 1: Download APK

Grab the latest APK from the [Releases](https://github.com/skepsun/kototoro/releases) page.

### Option 2: Build from Source

```bash
# 1. Clone the repository
git clone https://github.com/skepsun/Kototoro.git
cd Kototoro

# 2. Build Debug version
./gradlew assembleDebug
```

## Usage

### Foldable devices

- Split-view reading (list and content side by side)
- Adaptive layout for different form factors

### WebDAV Sync

1. **Configure WebDAV** - Set up your WebDAV server in "Settings → Backup & Restore"
2. **Auto Backup** - Reading progress, favorites, and history automatically sync to cloud
3. **Multi-Device Sync** - Seamlessly sync reading state across different devices
4. **Smart Merge** - Timestamp-based intelligent merging to avoid data conflicts

### Video playback

1. On supported video sources, tap “Play” on the detail screen to open the built-in player.
2. Use the top-right “Orientation” action to toggle or lock screen rotation (landscape by default).
3. Use the “Quality” action to switch between available tracks when provided by the source.
4. If a direct stream cannot be resolved, Kototoro opens the original page in the in-app browser.

### Novel Reading

1. **Online Reading** - Read online novels in the built-in reader
2. **EPUB Download** - Download and read EPUB format e-books
3. **Reading Settings** - Customize font size, line spacing, background color, etc.
4. **Chapter Navigation** - Quick jump to specific chapters

### Check for Updates

1. Go to "Settings → About → Version"
2. Tap on the version number to check for updates
3. If a new version is available, update details and download link will be shown

**Note**: If you build the app with your own signature, you need to update the certificate SHA256 to use the update check feature. See [APP_UPDATE_FIX.md](../APP_UPDATE_FIX.md)


## Development

### Requirements

- Android Studio 2022.3+
- JDK 17+
- Android SDK 33+
- Gradle 9.0+

### Project Structure

```
Kototoro/
├── app/                    # Main application module
├── kototoro-parsers/       # Parser module
├── .github/workflows/      # CI/CD configuration
└── metadata/              # App metadata
```

### Contributing

Issues and pull requests are welcome!

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project follows the license of Kotatsu. See [LICENSE](LICENSE).

## Acknowledgements

- **Kotatsu Team** - Original manga reader and parser framework
- **Venera Team** - Feature-rich open-source reader with excellent Chinese parsers
- **All Contributors** - Thanks to everyone who has contributed to this project

## Contact

- GitHub Issues: https://github.com/skepsun/kototoro/issues
- Email: chuxiongsun@gmail.com

## 🗺️ Roadmap

- [ ] Improve EPUB reader functionality (save reading progress)
- [ ] Add more Chinese manga/novel site support
- [ ] Optimize video playback experience
- [ ] Improve WebDAV sync mechanism

---

⭐ If you find this project helpful, please give it a star!