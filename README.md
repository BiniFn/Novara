# Kototoro

![App Icon](metadata/en-US/icon.png)

![version](https://img.shields.io/badge/version-0.8.7-blue)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/xBXvPz7tr7)
[![Docs](https://img.shields.io/badge/docs-hub-blue)](https://kototoro-app.github.io/Kototoro/)
[![Getting Started](https://img.shields.io/badge/docs-getting--started-blue)](https://kototoro-app.github.io/Kototoro/getting-started)
[![Translation Docs](https://img.shields.io/badge/docs-auto--translation-blue)](https://kototoro-app.github.io/Kototoro/automatic-translation)

<!-- ## Development Philosophy

Kototoro is proudly built with the assistance of AI large language models. However, while modern AI is fantastic at generating boilerplate and scaffolding (the "0 to 1"), the reality of shipping a production-ready reader dealing with complex video buffering, OCR pipelines, JNI, and memory leaks is a purely human endeavor.

The past 6 months of development have been dedicated to relentless debugging, performance tuning, and architectural refactoring to make Kototoro the robust platform it is today. To anyone (especially @hxncvxz) who dismisses this intensive engineering effort as mere "vibecoding": you are warmly invited to fix the next core memory leak or Vulkan synchronization bug yourself. 😉
<!-- 
<div align="center">
    <img src="./metadata/en-US/images/Screenshot_20260405_115114_com_discord_MainActivi.jpg" alt="Discord chat log: vibecoder accusation" width="300"/>
    <br/>
    <em>公道自在人心</em>
</div> --> -->

Kototoro is an open-source Android app that brings manga, novels, and video into one reader. It combines broad source compatibility with local OCR + translation, video super-resolution, and WebDAV-based multi-device sync.

## Why Kototoro

- One app for manga, novels, and video
- Local automatic OCR + translation directly inside the reader
- Video super-resolution (Anime4K), DLNA casting, subtitle and audio track selection
- Tracking discovery across MAL, Kitsu, AniList, Bangumi, Shikimori, and MangaUpdates
- Broad source support: external dynamic parsers, Mihon, Aniyomi, IReader, Legado, TVBox
- Dynamic zero-overhead UI plugins via external classloaders
- Fast pure-Kotlin OTA delta updates
- Site favorites import and synchronization for supported services

## Start Here

- Download the latest APK from [Releases](https://github.com/Kototoro-app/Kototoro/releases)
- Complete the in-app **Setup wizard** right after installation to configure GitHub mirrors, download core source plugins, and set up your content types.
- Read the [Documentation Website](https://kototoro-app.github.io/Kototoro/)
- Follow [Getting Started](https://kototoro-app.github.io/Kototoro/getting-started) for detailed wizard instructions and next steps
- Learn the core product surface in [Reader Features](https://kototoro-app.github.io/Kototoro/reader-features)
- Set up [Automatic Translation](https://kototoro-app.github.io/Kototoro/automatic-translation)
- Set up [Source Integrations](https://kototoro-app.github.io/Kototoro/source-integrations)
- Set up [WebDAV Sync](https://kototoro-app.github.io/Kototoro/webdav-sync)
- Check [FAQ](https://kototoro-app.github.io/Kototoro/faq) and [Troubleshooting](https://kototoro-app.github.io/Kototoro/troubleshooting) if something does not work
- See [Development](https://kototoro-app.github.io/Kototoro/development) and [Contributing](https://kototoro-app.github.io/Kototoro/contributing) if you want to work on the project

## External Source Ecosystems

Kototoro supports several important external source ecosystems. These repositories are a key part of real-world setup for many users.

### Common Mihon source repositories

- [Keiyoushi Extensions](https://github.com/keiyoushi/extensions)
- [Yuzono Tachiyomi Extensions](https://github.com/yuzono/tachiyomi-extensions)
- [LittleSurvival CopyManga Copy20](https://github.com/LittleSurvival/copymanga-copy20)

### Common Aniyomi source repositories

- [Kohi-den Extensions Source](https://github.com/Kohi-den/extensions-source)
- [Yuzono Anime Extensions](https://github.com/yuzono/anime-extensions)

### Common Legado source repository

- [XIU2 Yuedu](https://github.com/XIU2/Yuedu)

For setup details, see [Source Integrations](https://kototoro-app.github.io/Kototoro/source-integrations).

## Screenshots

<div align="center">
    <img src="./metadata/en-US/images/tabletScreenshots/1.jpg" alt="All sources" width="260"/>
    <img src="./metadata/en-US/images/tabletScreenshots/3.jpg" alt="Video list view" width="260"/>
    <img src="./metadata/en-US/images/tabletScreenshots/5.jpg" alt="Comic reading view" width="260"/>
    <img src="./metadata/en-US/images/tabletScreenshots/7.jpg" alt="Novel reading view" width="260"/>
</div>

## Contributing

Issues and pull requests are welcome. Start with [Contributing](https://kototoro-app.github.io/Kototoro/contributing).

## Disclaimer

The developer(s) of this open-source application does not have any affiliation with the content providers available, nor do the developers maintain or govern any extension repositories. This application hosts and bundles zero content. All content parsing is strictly provided by user-installed, third-party extensions and sources. The developers assume no liability or responsibility for any content accessed through user-configured endpoints.

## License

```text
Copyright © 2024 Kototoro Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgements

- [Mihon](https://github.com/mihonapp/mihon)
- [Yomihon](https://github.com/yomihon/yomihon)
- [Venera](https://github.com/venera-app/venera)
- [Kazumi](https://github.com/Predidit/Kazumi)
- [Light Novel Yuedu Source](https://github.com/ZWolken/Light-Novel-Yuedu-Source)
- [legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)
- [RealCUGAN-ncnn-Android](https://github.com/omeshi1/RealCUGAN-ncnn-Android)

## Contact

- Discord: [Join Server](https://discord.gg/xBXvPz7tr7)
- GitHub Issues: [Issue Tracker](https://github.com/Kototoro-app/Kototoro/issues)

