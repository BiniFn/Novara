# Kototoro - 漫画/小说阅读器

![应用图标](metadata/en-US/icon.png)

[![中文](https://img.shields.io/badge/语言-中文-blue)](README.md) [![English](https://img.shields.io/badge/Language-English-blue)](README_en.md)

## 📖 项目简介

**Kototoro** 是一个基于 **Kotatsu** 的非官方分支项目，专注于为中国用户提供更好的漫画、视频和小说阅读体验。

### 🎯 主要特性

#### 🌟 核心功能
- ✅ **中文网站支持** - 优化了对中文漫画/小说网站的支持
- ✅ **继承Kotatsu所有功能** - 完整的漫画阅读器功能
- 🎬 **视频网站支持** - 内置视频播放器，支持清晰度切换
- 📚 **小说阅读支持** - 支持在线小说阅读和EPUB下载
- 📱 **折叠屏阅读支持** - 完美适配折叠屏设备，分屏显示
- ☁️ **WebDAV自动同步** - 自动备份和同步阅读进度，支持多设备


#### 已知问题
- 视频播放和进度保存不稳定
- EPUB小说能下载和阅读，但是章节跳转和进度保存有问题

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


## 🛠️ 技术栈

- **Kotlin** - 主要开发语言
- **Android Jetpack** - 现代Android开发架构
- **Kotatsu Parser** - 漫画/小说解析器框架
- **WebDAV** - 云同步协议
- **Trae** - AI IDE辅助开发

## 📦 安装

### 方式一：下载APK

从 [Releases页面](https://github.com/skepsun/kototoro/releases) 下载最新版本的APK文件。

### 方式二：从源码构建

```bash
# 1. 克隆项目
git clone https://github.com/skepsun/Kototoro.git
cd Kototoro

# 2. 构建Debug版本
./gradlew assembleDebug
```

## 🎮 使用指南

### 📱 折叠屏使用

1. **分屏阅读** - 在折叠屏上分屏显示目录和内容
2. **自适应布局** - 自动适应不同的屏幕形态
3. **横屏优化** - 支持横屏双页阅读模式

### ☁️ WebDAV同步

1. **配置WebDAV** - 在"设置 → 备份与恢复"中配置WebDAV服务器
2. **自动备份** - 阅读进度、收藏、历史记录自动同步到云端
3. **多设备同步** - 在不同设备间无缝同步阅读状态
4. **智能合并** - 基于时间戳的智能合并，避免数据冲突

### 🎬 视频播放

1. 在支持的视频站点详情页点击"播放"，进入内置播放器
2. 右上角"旋转"按钮可横竖屏切换或锁定当前方向（默认横屏）
3. 右上角"清晰度"按钮可切换可用的画质轨道（如站点提供）
4. 若直链解析失败，将自动使用内置浏览器打开原页面

### 📖 小说阅读

1. **在线阅读** - 支持在内置阅读器中阅读在线小说
2. **EPUB下载** - 支持下载EPUB格式电子书
3. **阅读设置** - 可调整字体大小、行距、背景色等
4. **章节导航** - 快速跳转到指定章节

### 🔄 检查更新

1. 进入"设置 → 关于 → 版本"
2. 点击版本号检查更新
3. 如果有新版本，会显示更新详情和下载链接

**注意**: 如果你使用自己的签名构建应用，需要更新证书SHA256才能使用检查更新功能。详见 [APP_UPDATE_FIX.md](../APP_UPDATE_FIX.md)

## 🔧 开发

### 环境要求

- Android Studio 2022.3+
- JDK 17+
- Android SDK 33+
- Gradle 9.0+

### 项目结构

```
Kototoro/
├── app/                    # 主应用模块
├── kototoro-parsers/       # 解析器模块
├── .github/workflows/      # CI/CD配置
└── metadata/              # 应用元数据
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

- **Kotatsu团队** - 原始项目的开发者，提供漫画阅读器和解析器框架
- **Venera团队** - 另一个优秀的功能强大的开源漫画阅读器项目，并提供优秀的中文解析器代码
- **所有贡献者** - 感谢所有为本项目做出贡献的开发者

## 📞 联系方式

- **GitHub Issues**: [问题反馈](https://github.com/skepsun/kototoro/issues)
- **Email**: chuxiongsun@gmail.com

## 🗺️ 路线图

- [ ] 完善EPUB阅读器功能（保存阅读进度）
- [ ] 增加更多中文漫画/小说网站支持
- [ ] 优化视频播放体验
- [ ] 改进WebDAV同步机制

---

⭐ 如果这个项目对你有帮助，请给我们一个Star！
