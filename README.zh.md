# Kototoro - 中文漫画阅读器

![应用图标](metadata/en-US/icon.png)

[![中文](https://img.shields.io/badge/语言-中文-blue)](README.zh.md) [![English](https://img.shields.io/badge/Language-English-blue)](README.md)

## 📖 项目简介

**Kototoro** 是一个基于 **Kotatsu** 的非官方分支项目，专注于为中国用户提供更好的漫画阅读体验。

### 🎯 主要特性

#### 🌟 核心功能
- ✅ **中文网站支持** - 优化了对中文漫画网站的支持
- ✅ **继承Kotatsu所有功能** - 完整的漫画阅读器功能

#### 🚀 额外功能
- 📱 **折叠屏阅读支持** - 完美适配折叠屏设备
- ☁️ **WebDAV自动同步** - 自动备份和同步阅读进度
- 🎬 **视频网站支持** - 支持在部分站点解析视频直链并使用内置播放器播放；默认横屏并提供旋转/锁定按钮；支持清晰度切换（如果站点提供多轨）；解析失败时可回退到内置浏览器。

## 🛠️ 技术栈

- **Kotlin** - 主要开发语言
- **Android Jetpack** - 现代Android开发架构
- **Kotatsu Parser** - 漫画解析器框架
- **WebDAV** - 云同步协议
- **Trae** - AI IDE

## 📦 安装

### 从源码构建

```bash
# 克隆项目
git clone https://github.com/skepsun/Kototoro.git

# 进入项目目录
cd Kototoro

# 构建项目
./gradlew assembleDebug
```

### 下载APK

从 [Releases页面](https://github.com/skepsun/kototoro/releases) 下载最新版本的APK文件。

## 🎮 使用指南

### 折叠屏使用

1. **分屏阅读** - 在折叠屏上分屏显示目录和内容
2. **自适应布局** - 自动适应不同的屏幕形态

### WebDAV同步

1. **配置WebDAV** - 在设置中配置WebDAV服务器信息
2. **自动备份** - 阅读进度自动同步到云端
3. **多设备同步** - 在不同设备间同步阅读状态

### 视频播放

1. 在支持的视频站点详情页点击“播放”，进入内置播放器。
2. 右上角“旋转”按钮可横竖屏切换或锁定当前方向（默认横屏）。
3. 右上角“清晰度”按钮可切换可用的画质轨道（如站点提供）。
4. 若直链解析失败，将自动使用内置浏览器打开原页面。

## 🔧 开发

### 环境要求

- Android Studio 2022.3+
- JDK 17+
- Android SDK 33+

### 贡献指南

欢迎提交Issue和Pull Request！

## 📝 许可证

本项目基于 **Kotatsu** 的许可证，详情请查看 [LICENSE](LICENSE) 文件。

## 🤝 致谢

- **Kotatsu团队** - 原始项目的开发者，提供漫画阅读器和解析器框架
- **Venera团队** - 另一个优秀的功能强大的开源漫画阅读器项目，并提供优秀的中文解析器代码

## 📞 联系方式

- **GitHub Issues**: [问题反馈](https://github.com/skepsun/kototoro/issues)
- **Email**: chuxiongsun@gmail.com

---

⭐ 如果这个项目对你有帮助，请给我们一个Star！