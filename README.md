# Kototoro - 漫画/小说/视频阅读器

![应用图标](metadata/en-US/icon.png)

[![中文](https://img.shields.io/badge/语言-中文-blue)](README.md) [![English](https://img.shields.io/badge/Language-English-blue)](README_en.md) ![version](https://img.shields.io/badge/version-0.2.2-blue)

## 📖 项目简介

**Kototoro** 是一个基于 **Kotatsu** 的非官方分支项目，专注于为中国用户提供更好的漫画、小说和视频阅读播放体验。

### 🚀 最近更新 (v0.2.2)
1. **Mihon 扩展支持**：完整支持 Mihon 扩展插件，极大地扩展了漫画源。
2. **漫画源管理系统**：实现了完善的漫画源管理系统，包含网络请求、UI 组件及源导入功能。
3. **本地漫画导入**：完善了本地漫画的导入逻辑。
4. **性能与稳定性**：
    - 改进了双页模式的索引逻辑。
    - 增强了 ProGuard 混淆规则，提高 Release 版本的稳定性。
    - 修复了 URL 转换逻辑中的潜在问题。
5. **CI/CD 优化**：自动生成 Release 更新日志，支持手动触发发布。

### 🎯 主要特性

#### 🌟 核心功能
- ✅ **中文化优化** - 深度优化对中文漫画、视频和小说站点的适配
- ✅ **全能阅读器** - 继承 Kotatsu 所有优秀的漫画阅读功能
- ✅ **视频播放** - 支持在线视频流播放，具备清晰度切换、锁定旋屏等功能
- ✅ **增强型小说阅读** - 支持在线/本地小说无缝切换阅读、图片显示、插图章节及 EPUB 下载
- ✅ **下载管理** - 支持自定义下载延迟（规避频率限制），下载过程更加稳定
- ✅ **折叠屏适配** - 完美适配折叠屏设备，支持双页模式与自适应布局
- ✅ **WebDAV 同步** - 跨设备自动备份/恢复收藏、历史、分组、登录凭据等数据
- ✅ **站点收藏导入和同步** - 支持从已登录的站点导入收藏到本地，或将本地收藏同步到站点
- ✅ **Mihon 扩展支持** - 完整支持 Mihon 扩展插件，极大地扩展了漫画源。

## 应用截图
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

## 🎮 使用指南

### 🧩 Mihon 扩展插件指南

Kototoro 现已支持 Mihon (原 Tachiyomi) 的扩展插件系统，您可以按以下步骤添加并使用更多漫画源：

1. **安装并配置 Mihon**：
   - 安装 [Mihon](https://mihon.app/) 官方应用。
   - 在 Mihon 中添加扩展插件仓库（如 [Keiyoushi](https://keiyoushi.github.io/extensions/)）。
   - 在 Mihon 中下载并安装您需要的漫画源插件。
2. **在 Kototoro 中使用**：
   - 打开 Kototoro，点击底部导航栏的 "浏览"。
   - 切换到 "Mihon" 标签页，应用会自动检测手机中已安装的 Mihon 插件。
   - 您可以直接在此浏览并使用这些插件源。
3. **强制刷新检测**：
   - 如果新安装的插件没有出现，可以进入 "设置" → "内容源" → "Mihon 扩展"。
   - 在此页面下拉刷新，强制系统重新检测已安装的插件。

> [!NOTE]
> Kototoro 通过检测系统中安装的 Mihon 插件 APK 来实现兼容，因此请确保插件已正确安装在您的设备上。

### ☁️ WebDAV同步

1. **配置WebDAV** - 在"设置 → 备份与恢复"中配置WebDAV服务器
2. **自动备份** - 阅读进度、收藏、历史记录自动同步到云端
3. **多设备同步** - 在不同设备间无缝同步阅读状态
4. **智能合并** - 基于时间戳的智能合并，避免数据冲突

### 📥 站点收藏导入/同步

支持从已登录的站点导入收藏到本地，或将本地收藏同步到站点。

#### 支持的站点

| 站点 | 导入收藏 | 同步收藏 | 备注 |
| :--- | :---: | :---: | :--- |
| CopyManga | ✅ | ✅ | 需登录 |
| 再漫画 | ✅ | ✅ | 需登录 |
| Komiic | ✅ | ✅ | 需登录 |
| 包子漫画 | ✅ | ✅ | 需登录 |
| 漫画柜 | ✅ | ✅ | 需登录 |
| 绅士漫画 | ✅ | ✅ | 需登录 |
| 哔咔漫画 | ✅ | ✅ | 需登录 |

#### 使用方法

1. **导入收藏**
   - 进入 "收藏" 页面 → 点击右上角菜单 → 选择 "从站点导入"
   - 选择已登录的站点，点击导入
   - 导入的收藏会自动创建对应的站点分组（如"CopyManga"）

2. **同步收藏**
   - 进入 "收藏" 页面 → 点击右上角菜单 → 选择 "同步到站点"
   - 选择目标站点，将本地收藏推送到站点

3. **自动分组**
   - 从站点导入的收藏会自动归入以站点名称命名的分组
   - 已存在同名分组时会自动合并，不会创建重复分组
   - 手动添加收藏时也会根据来源自动归类


## 🛠️ 技术栈

- **Kotlin** - 主要开发语言
- **Android Jetpack** - 现代Android开发架构
- **Kotatsu Parser** - 漫画/小说解析器框架
- **WebDAV** - 云同步协议
- **AI IDEs** - AI IDE辅助开发

## 📦 安装


从 [Releases页面](https://github.com/skepsun/kototoro/releases) 下载最新版本的APK文件。


## 🔧 开发

### 环境要求

- Android Studio 2022.3+
- JDK 17+
- Android SDK 33+
- Gradle 9.0+

### 项目结构

```
kototoro_demo/                    # 开发目录
├── Kototoro/                    # 主应用仓库
│   ├── app/                     # 主应用模块
│   ├── gradle/                  # Gradle 配置
│   ├── .github/workflows/       # CI/CD 配置
│   └── metadata/                # 应用元数据（截图等）
│
└── kototoro-parsers/            # 解析器仓库（独立）
    └── src/main/kotlin/.../site/  # 各站点解析器
```

### 贡献指南

欢迎提交Issue和Pull Request！

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 📝 许可证

本项目基于 **Kotatsu** 的许可证，详情请查看 [LICENSE](LICENSE) 文件。

## 🤝 致谢

- **[Kotatsu](https://github.com/KotatsuApp/Kotatsu)** - 原始项目的开发者，提供漫画阅读器和解析器框架
- **[Mihon](https://github.com/mihonapp/mihon)** - 优秀的开源漫画阅读器，本项目集成了其插件系统
- **[Venera](https://github.com/venera-app/venera)** - 另一个优秀的功能强大的开源漫画阅读器项目，并提供优秀的中文解析器代码
- **[阅读轻小说源](https://github.com/ZWolken/Light-Novel-Yuedu-Source)** - 提供了一部分轻小说源的参考代码
- **大模型** - claude、gemini、gpt

## 📞 联系方式

- **GitHub Issues**: [问题反馈](https://github.com/skepsun/kototoro/issues)
- **Email**: chuxiongsun@gmail.com



---

⭐ 如果这个项目对你有帮助，请给我们一个Star！
