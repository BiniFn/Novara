# Kototoro - 漫画/小说阅读器

![应用图标](metadata/en-US/icon.png)

[![中文](https://img.shields.io/badge/语言-中文-blue)](README.md) [![English](https://img.shields.io/badge/Language-English-blue)](README_en.md)

## 📖 项目简介

**Kototoro** 是一个基于 **Kotatsu** 的非官方分支项目，专注于为中国用户提供更好的漫画、视频和小说阅读体验。

### 🚀 最近更新 (v2.x)

1. **混合小说加载架构**：实现已下载章节读本地、未下载章节读在线的无缝切换，彻底解决阅读断点。
2. **新增大量阅读源**：新增 `哔哩轻小说`、`深奏`、`轻小说仓库` 等优质源，优化对 `Wenku8`、`BatoTo` 的解析。
3. **小说多媒体增强**：支持在线小说插图加载，针对纯图片章节自动进入高清图片预览模式。
4. **下载稳定性提升**：新增章节下载延迟设置，大幅降低大批量下载时触发站点频率限制（SSL EOF）的风险。
5. **本地导入修复**：修复了在部分 Android 版本下，多漫画批量本地导入后封面丢失的问题。
6. **体验优化**：全面更新 UI 文案，修复历史记录与收藏中元数据丢失的问题，阅读体验更顺滑。

### 🌐 已支持网站及功能

以下是已内置的部分中文源及核心国际源的功能支持情况：

| 网站名称 | 分类 | 搜索 | 筛选 | 登录/下载 | 特色功能 |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **哔哩轻小说 (Bilinovel)** | 小说 | ✅ | ❌ | ❌/✅ | 插图渲染、多镜像自动切换 |
| **笔趣阁 (Biquge)** | 小说 | ✅ | ✅ | ❌/✅ | 资源量大、加载速度快 |
| **轻小说文库 (Wenku8)** | 小说 | ✅ | ✅ | ✅/✅ | 老牌源、稳定性极高 |
| **神凑轻小说 (Shencou)** | 小说 | ✅ | ❌ | ❌/✅ | 简洁无广告 |
| **轻之国度 (LKNovel)** | 小说 | ✅ | ❌ | ❌/✅ | 专业轻小说社区 |
| **轻小说百科 (LightNovelWiki)** | 小说 | ✅ | ❌ | ❌/✅ | 资料详实 |
| **Bato.To** | 漫画 | ✅ | ✅ | ✅/✅ | 全球化多语言源、自动重试 |
| **绅士漫画 (Wnacg)** | 漫画 | ✅ | ✅ | ❌/✅ | 经典同人本子站 |
| **禁漫天堂 (JmComic)** | 漫画 | ✅ | ✅ | ❌/✅ | 极速更新及分类 |
| **哔咔漫画 (Picacg)** | 漫画 | ✅ | ✅ | ✅/✅ | 稳定分流、老牌本子站 |
| **拷贝漫画 (CopyManga)** | 漫画 | ✅ | ✅ | ❌/✅ | 画质极高、大陆访问稳定 |
| **包子漫画 (Baozimh)** | 漫画 | ✅ | ✅ | ❌/✅ | 更新极其迅速 |
| **漫画柜** | 漫画 | ✅ | ✅ | ❌/✅ | 传统老牌漫画站 |
| **再漫画 (Zaimanhua)** | 漫画 | ✅ | ✅ | ❌/✅ | 纯净漫画体验 |
| **优酷漫画 (YKMH)** | 漫画 | ✅ | ✅ | ❌/✅ | 辅助补充源 |
| **CCC 追漫台** | 漫画 | ✅ | ✅ | ❌/✅ | 台湾正版漫画采集 |
| **Komiic** | 漫画 | ✅ | ✅ | ❌/✅ | 台湾漫画社区 |
| **AGE 动漫 (Age)** | 视频 | ✅ | ✅ | ❌/❌ | 动漫资源极其丰富 |
| **动漫巴士 (DMBUS)** | 视频 | ✅ | ✅ | ❌/❌ | 备用动画源 |
| **二矿动漫 (Erkuang)** | 视频 | ✅ | ✅ | ❌/❌ | 界面友好 |
| **Hanime1** | 视频 | ✅ | ✅ | ❌/❌ | 高清成人动漫、清晰度切换 |


> [!TIP]
> 更多小众源（如：百合会、漫小肆、94MT等）请在应用内“源管理”中直接搜索添加。

### 🎯 主要特性

#### 🌟 核心功能
- ✅ **中文化优化** - 深度优化对中文漫画、视频和小说站点的适配
- ✅ **全能阅读器** - 继承 Kotatsu 所有优秀的漫画阅读功能
- ✅ **视频播放** - 支持在线视频流播放，具备清晰度切换、锁定旋屏等功能
- ✅ **增强型小说阅读** - 支持在线/本地小说无缝切换阅读、图片显示、插图章节及 EPUB 下载
- ✅ **下载管理** - 支持自定义下载延迟（规避频率限制），下载过程更加稳定
- ✅ **折叠屏适配** - 完美适配折叠屏设备，支持双页模式与自适应布局
- ✅ **WebDAV 同步** - 跨设备自动备份和同步收藏、历史及阅读进度

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

## 🛠️ 开发解析器（源）引导

本项目采用高度解耦的解析器框架，支持快速添加漫画、小说或视频源。

### 1. 环境准备
```bash
# 克隆应用主仓库与解析器仓库到同一父目录下
git clone https://github.com/skepsun/Kototoro.git
git clone https://github.com/skepsun/kototoro-parsers.git

# 开启本地调试模式：在 Kototoro/settings.gradle 末尾取消注释 includeBuild 部分
# 这将使应用直接使用本地的 kototoro-parsers 源码进行编译，方便实时调试
```

### 2. 开发流程
1. **创建解析器**：在 `kototoro-parsers/src/main/kotlin/org/skepsun/kototoro/parsers/site/zh` 目录下新建 Kotlin 类。
   - **漫画源**：参考 `Baozimh.kt` (HTML 解析) 或 `KomiicParser.kt` (API 解析)。
   - **小说源**：参考 `Bilinovel.kt` (带图片/复杂逻辑) 或 `Shencou.kt` (简洁 HTML)。
   - **视频源**：参考 `Hanime1.kt`。
2. **注册源**：使用 `@MangaSourceParser` 注解定义唯一 ID、名称及内容类型。
3. **实现核心方法**：
   - `getListPage()`: 首页/分类/搜索列表加载。
   - `getDetails()`: 详情页数据加载及章节列表获取。
   - `getPages()`: 获取具体章节的内容链接（图片或 HTML）。
4. **测试与调试**：
   - 使用 `./gradlew installDebug` 安装到设备。
   - 打开 Android Studio 的 Logcat，过滤 `NovelReaderActivity` 或相关解析器 Tag 查看日志。
   - **Tip**: 推荐使用本项目集成的 AI 辅助功能进行代码分析与修复。

### 3. 常见技巧
- **规避反爬**：在解析器中实现 `Interceptor` 接口以自定义 Header、Referer 或处理 Cloudflare 验证。
- **图片代理**：如果站点开启了防盗链，可在 `getPages` 或拦截器中注入正确的 Referer。
- **数据格式**：小说解析器需通过 `NovelChapterContent` 返回 HTML 内容。


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

### 📖 小说阅读与管理

1. **混合阅读模式**：支持已下载章节本地读取，未下载章节实时在线加载，阅读无断点。
2. **下载优化**：针对某些源的频率限制，可在下载对话框中设置“下载延迟”（推荐 0.5s - 3s）。
3. **内容展示**：完美支持插图显示；对于纯图片章节（插画页），会自动开启沉浸式图片浏览模式。
4. **历史恢复**：从收藏或历史记录进入时，系统会自动同步远程元数据，确保目录完整。
5. **EPUB 下载**：支持将在线小说打包成标准的 EPUB 格式保存到本地。

### 🔄 检查更新

1. 进入"设置 → 关于 → 版本"
2. 点击版本号检查更新
3. 如果有新版本，会显示更新详情和下载链接


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
