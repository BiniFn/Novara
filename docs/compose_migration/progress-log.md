# Compose 迁移执行日志

## 说明

本页用于持续记录 Compose Multiplatform 与 liquid glass 迁移的真实进度，只记录已经完成或正在执行的事项，不记录空计划。

## 2026-04-16

### 已完成

- 新建迁移主文档：`compose_migration/cmp-liquid-glass-migration.md`
- 明确阶段划分、非目标与模块边界
- 新增基础玻璃组件：`GlassSurface`、`GlassTopBarContainer`、`GlassBottomBarContainer`
- 主顶栏与底栏已切换到统一玻璃容器
- `./gradlew :app:compileDebugKotlin --no-daemon` 验证通过
- `npm run docs:build` 验证通过
- `RootSettingsFragment` 已切换为 Compose 入口页
- 新增第一版设置入口组件：`SettingsRootScreen`
- Root Settings 改造后再次通过 Kotlin 编译与文档构建验证
- `StorageAndNetworkSettingsFragment` 已迁移到 Compose
- `StorageAndNetwork` 已补齐 choice / switch / text input 设置组件
- `StorageAndNetwork` 中 SSL bypass 提示已改为仅在用户开启时触发
- `AppearanceSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 multi-choice / slider
- Appearance 页保留 `NavConfigFragment` 与 `ProtectSetupActivity` 作为平台特定路由
- 本轮再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `ServicesSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 split switch，并为 action row 增加可选图标
- `Services` 页中的 Sync / Suggestions / Discord 继续复用既有 Android 路由，追踪服务授权确认已切换为 Compose `AlertDialog`
- `Services` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `DownloadsSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 info row
- `Downloads` 页中的目录选择、文档树授权、电池优化跳转继续保留为 Fragment 侧平台桥接
- `Downloads` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `TrackerSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已补齐 disabled / enabled-state 语义，覆盖旧 `Preference` 的 `dependency` 行为
- `Tracker` 页中的通知设置跳转、分类配置 sheet、电池优化请求和外部说明链接继续保留为 Fragment 侧平台桥接
- `Tracker` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `SourcesSettingsFragment` 已迁移到 Compose
- `Sources` 页中的 manage/catalog/json/extensions 子入口继续复用既有路由，`handle_links` 改为 Compose 驱动的平台开关
- 为 `Sources` 迁移补齐 `jar_priority_order` 等偏好可写接口
- `Sources` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `BackupsSettingsFragment` 已迁移到 Compose
- Compose 设置 DSL 已为文本输入补齐 placeholder / password 能力，`AppSettings` 已补齐 Backups 所需的 WebDAV 与周期备份可写接口
- `Backups` 页中的文档选择、持久化授权、备份服务启动与恢复对话框继续保留为 Fragment 侧平台桥接
- `Backups` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `NotificationSettingsLegacyFragment` 已迁移到 Compose
- `AppSettings` 已补齐通知振动与灯光偏好的可写接口
- `Notification` 页中的系统铃声选择继续保留为 Fragment 侧 `ActivityResultContract` 平台桥接
- `Notification` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `JsonSourcesRootFragment` 已迁移到 Compose
- 新增通用 Compose tab-host，用于承载 `childFragmentManager` 下的设置子 Fragment
- `Json Sources` 页中的 Legado / TVBox / LNReader 子 Fragment 继续保留为平台桥接，宿主本身不再依赖 `TabLayoutMediator` 与 `ViewPager2`
- `Json Sources` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `ExtensionsRootFragment` 已迁移到 Compose
- `Extensions` 页复用同一套 Compose tab-host，Mihon / Aniyomi / IReader 子 Fragment 继续保留为平台桥接
- `Extensions` 迁移后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- `MainActivity` 已开始接管主壳内容避让，`FragmentContainerView` 的顶栏/底栏可见 inset 改由 Compose chrome 回传并统一注入 padding
- `KototoroApp` 不再直接对 Android `Fragment` 宿主施加内部 padding，浮动底栏时也不再通过伪造 child insets 间接避让
- Compose 顶栏搜索建议点击已对齐旧实现：`Content` 直达详情、`Tag` 保持筛选条件进入标签搜索、`Source/SourceTip` 直达列表、`Author` 进入作者搜索
- 普通搜索、最近搜索与提示词点击已补齐链接直达逻辑，主壳内容类型与来源筛选也会同步到 `SearchSuggestionViewModel`
- 主壳收口后再次通过 `./gradlew :app:compileDebugKotlin --no-daemon` 验证
- 文档站在本轮结束后再次通过 `npm run docs:build` 验证

### 进行中

- 整理 Compose 设置 DSL
- 继续推进 Main Shell 与导航壳收口

### 下一步

- 继续推进 Main Shell 与导航壳收口
- 继续补齐设置 DSL 中剩余复杂组件
