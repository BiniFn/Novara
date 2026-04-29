# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Kototoro 是一个开源的 Android 应用，将漫画、小说和视频整合到一个阅读器中。核心特性包括：
- 本地 OCR + 机器翻译
- 视频超分辨率（Anime4K）
- 多平台进度追踪（MAL、Kitsu、AniList、Bangumi 等）
- 广泛的图源支持：Mihon、Aniyomi、IReader、Legado、TVBox 扩展
- 动态 UI 插件系统（通过外部 classloader）
- 纯 Kotlin 实现的 OTA 增量更新（bspatch）
- WebDAV 多设备同步

## 构建和测试命令

### 基础构建
```bash
# 构建 debug APK
./gradlew :app:assembleDebug

# 构建 release APK（需要签名配置）
./gradlew :app:assembleRelease

# 仅编译 Kotlin 代码（最快的验证方式）
./gradlew :app:compileDebugKotlin --no-daemon

# 清理构建
./gradlew clean
```

### 测试
```bash
# 运行 JVM 单元测试
./gradlew :app:testDebugUnitTest --no-daemon

# 运行设备/模拟器上的 instrumented 测试
./gradlew :app:connectedDebugAndroidTest

# 运行所有检查
./gradlew :app:check
```

### 文档
```bash
# 启动本地文档站点（VitePress）
npm ci && npm run docs:dev

# 构建静态文档
npm run docs:build
```

### 版本信息
```bash
# 获取版本号
./gradlew printVersionName
./gradlew printVersionCode
```

## 项目架构

### 模块结构
- `app/` - 主应用模块，包含所有功能实现
- `parser-api/` - 共享的解析器接口定义
- `docs/` - VitePress 文档站点

### 代码组织（app/src/main/kotlin/org/skepsun/kototoro/）
项目按功能模块组织，每个模块通常包含 `data`、`domain`、`ui` 三层：

**核心模块**：
- `core/` - 核心基础设施（数据库、网络、缓存、异常处理、模型）
  - `core/db/` - Room 数据库、DAO、实体、迁移
  - `core/network/` - OkHttp 拦截器、代理、Cookie 管理、WebView 集成
  - `core/parser/` - 解析规则引擎
  - `core/model/` - 核心数据模型
- `main/` - 主入口 Activity

**内容源集成**：
- `mihon/` - Mihon/Tachiyomi 扩展集成（动态 ClassLoader、依赖注入桥接）
- `aniyomi/` - Aniyomi 扩展集成
- `ireader/` - IReader 源集成
- `extensions/` - 扩展管理框架
- `local/` - 本地文件导入（CBZ、EPUB 等）

**内容管理**：
- `home/` - 主页和内容列表
- `discover/` - 发现和浏览
- `search/` - 搜索功能
- `details/` - 内容详情页
- `favourites/` - 收藏管理
- `history/` - 历史记录
- `bookmarks/` - 书签

**阅读体验**：
- `reader/` - 漫画/小说阅读器
- `video/` - 视频播放器（超分辨率、DLNA）
- `image/` - 图片处理和 OCR + 翻译

**同步和备份**：
- `sync/` - WebDAV 同步
- `backups/` - 备份和恢复
- `tracker/` - 外部平台进度追踪
- `scrobbling/` - 进度同步

**其他功能**：
- `settings/` - 设置界面
- `download/` - 下载管理
- `browser/` - 内置浏览器（Cloudflare 绕过）
- `widget/` - 桌面小部件

### 关键技术实现

**外部扩展集成**（参考 `docs/architecture/external-extension-integration-guide.md`）：
- 使用 ChildFirstPathClassLoader 隔离扩展依赖
- 通过依赖注入桥接提供 Application Context 和网络实例
- 动态监听 APK 安装/卸载事件（BroadcastReceiver）
- 处理 Cloudflare 挑战和 Cookie 同步

**增量 OTA 更新**（参考 `docs/architecture/incremental-updates.md`）：
- CI/CD 使用 `bsdiff` 生成增量补丁
- 纯 Kotlin 实现的 `bspatch` 算法（无 NDK 依赖）
- 严格的版本匹配验证
- 自动回退到完整 APK 下载

**数据库**：
- Room 数据库，schema 位于 `app/schemas/`
- 大量迁移文件（Migration1To2 到 Migration26To27）
- 使用 KSP 生成 Kotlin 代码

**依赖注入**：
- Hilt/Dagger 用于依赖注入
- 需要在修改后运行 KSP 处理器

**测试框架**：
- JUnit5 + Kotest + MockK（单元测试）
- AndroidX Test + Hilt Testing（instrumented 测试）
- MockWebServer（网络测试）

## 开发注意事项

### 签名配置
Release 构建需要在 `local.properties` 或环境变量中配置签名：
```properties
RELEASE_STORE_FILE=/path/to/keystore
RELEASE_STORE_PASSWORD=***
RELEASE_KEY_ALIAS=***
RELEASE_KEY_PASSWORD=***
```

### 本地属性
`local.properties` 中可配置：
- `tg_backup_bot_token` - Telegram 备份机器人 token
- `dandanplay.appId` / `dandanplay.appSecret` - 弹弹 Play API 凭证

### 版本管理
在 `app/build.gradle` 中更新：
- `versionCode` - 每次发布递增
- `versionName` - 语义化版本号（如 "0.9.2"）

### 代码风格
- 遵循 `.editorconfig`：UTF-8、LF、4 空格缩进、120 字符行宽
- Kotlin 官方代码风格，启用尾随逗号
- 类名使用 PascalCase，方法和属性使用 camelCase
- Android 资源使用小写下划线命名（如 `pref_appearance.xml`）

### 提交规范
推荐使用 Conventional Commits 格式：
- `feat: ...` - 新功能
- `fix(scope): ...` - 修复
- `docs: ...` - 文档
- `chore: ...` - 杂项

### 翻译
翻译内容通过 Weblate 管理，避免手动批量修改字符串资源。

### 发布流程
参考 `.github/RELEASE_GUIDE.md`：
1. 更新 `versionCode` 和 `versionName`
2. 创建 Git 标签（如 `v1.0.0`）
3. 推送标签触发 GitHub Actions 自动构建
4. CI 自动生成 APK 和增量补丁并创建 Release

## 重要文档

- `docs/contributing.md` - 贡献指南
- `docs/architecture/external-extension-integration-guide.md` - 外部扩展集成详解
- `docs/architecture/incremental-updates.md` - 增量更新机制
- `docs/unified_source_management.md` - 源管理 UI 重构方案（草案）
- `.github/RELEASE_GUIDE.md` - 发布指南

## 常见任务

### 添加新的数据库迁移
1. 在 `core/db/migrations/` 创建新的 `MigrationXToY.kt`
2. 在 `KototoroDatabase` 中注册迁移
3. 更新 `app/schemas/` 中的 schema JSON

### 添加新的源类型
1. 在对应模块（如 `mihon/`、`ireader/`）实现源接口
2. 注册到源管理系统
3. 添加 UI 入口（通常在 `settings/sources/`）

### 修改阅读器功能
1. 核心逻辑在 `reader/` 模块
2. 图片处理在 `image/` 模块
3. 视频播放在 `video/` 模块
4. 测试时验证不同内容类型（漫画、小说、视频）

### 调试网络问题
1. 检查 `core/network/` 中的拦截器
2. 查看 `browser/` 模块的 WebView 集成
3. 验证 Cookie 和代理配置


# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文代码审查规范——在保持专业严谨的同时，用符合国内团队文化的方式给出有效反馈
- **chinese-commit-conventions**: 中文 Git 提交规范 — 适配国内团队的 commit message 规范和 changelog 自动化
- **chinese-documentation**: 中文技术文档写作规范——排版、术语、结构一步到位，告别机翻味
- **chinese-git-workflow**: 适配国内 Git 平台和团队习惯的工作流规范——Gitee、Coding、极狐 GitLab、CNB 全覆盖
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发或执行实现计划之前使用——创建具有智能目录选择和安全验证的隔离 git 工作树
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
