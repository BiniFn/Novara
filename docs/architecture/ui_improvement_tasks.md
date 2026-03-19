# Kototoro UI 改进实施任务清单

## 文档版本
- 创建日期：2026-03-18
- 最后更新：2026-03-19
- 状态：进行中
- 关联文档：[ui_improvement.md](./ui_improvement.md)

---

## 目标

将 `ui_improvement.md` 中的设计方案拆分为可执行任务，并明确当前已落地进度、未完成部分和下一步顺序。

约束如下：

- 先稳定主页与主导航信息架构
- 再完成 `Explore` 过滤器体验优化
- 追踪站点能力必须基于站点无关抽象扩展，不能把 Bangumi 写死为唯一实现
- 优先交付最小可运行闭环，避免过度设计

---

## 当前实现进度（2026-03-19）

### 已完成或已实质落地

- `Issue 1` 已完成：`Home` 已接入主导航，默认导航顺序已调整，并兼容现有导航配置入口。
- `Issue 2` 已实质完成：主页已从占位页推进为可用的 Dashboard 首版。
- `Issue 3` 已实质完成：主页状态已接入真实数据聚合，覆盖阅读历史、收藏、分类、更新、源数量、源分布、首选追踪站点与同步状态。
- `Issue 4` 已完成：导航配置页已兼容 `Home`。
- `Issue 5` 已完成：主页已作为默认导航首项接入启动链路。
- `Issue 6` 已完成：`Explore` 过滤器已改为两行固定布局，移除了横向滚动依赖。
- `Issue 7` 已实质完成：源类型图标体系已补齐到当前实现范围，额外新增了 `JavaScript` 源类型与图标，并打通到 `Explore`、搜索等共享标签模型。
- 追踪站点切换预留接口已落地：已新增站点无关的 discovery / matcher / preferred-site 抽象，以及默认实现骨架与 `AppSettings` 持久化入口。

### 当前主页首版包含内容

- 总览卡：最近阅读数量、未读更新数量、首选追踪站点、同步状态
- 继续阅读卡：阅读入口与详情入口
- 最近阅读卡：最近 3 条历史
- Library 卡：收藏总数、分类数、进入收藏页入口
- 最近更新卡：最近更新内容与章节增量
- 源概览卡：启用源数量、来源分布、进入源管理入口
- 快捷入口卡：`Reader settings` 与 `Settings`

### 已落地代码入口

- 主页与导航：
  - `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/NavItem.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/AppSettings.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/main/ui/MainNavigationDelegate.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/settings/nav/NavConfigViewModel.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/home/ui/HomeFragment.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/home/ui/HomeViewModel.kt`
  - `app/src/main/res/layout/fragment_home.xml`
- Explore：
  - `app/src/main/res/layout/fragment_explore.xml`
  - `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/SourceTag.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/explore/ui/model/BrowseGroupTab.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/search/domain/SearchSourceTypes.kt`
  - `app/src/main/res/drawable/ic_source_js.xml`
- 追踪站点 discovery 抽象：
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingSiteDiscoveryService.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingSiteMatcher.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/PreferredTrackingSiteProvider.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingDiscoveryModels.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/AppSettingsPreferredTrackingSiteProvider.kt`
  - `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/TrackingDiscoveryModule.kt`

### 尚未开始或尚未进入可交付状态

- `Issue 8`：主页卡片配置化尚未开始
- 详情页追踪信息卡尚未接入站点无关 discovery 抽象
- `Discover` 页面一期尚未开始
- 自动关联、手动纠正、追踪状态增强同步尚未开始
- Bangumi 本地缓存表与跨站点详情模型尚未进入最终形态

---

## 里程碑

### M1：主页与导航稳定
- `Issue 1`
- `Issue 2`
- `Issue 3`
- `Issue 4`
- `Issue 5`

状态：已完成或已实质完成

### M2：Explore 过滤器优化
- `Issue 6`
- `Issue 7`

状态：已完成或已实质完成

### M3：追踪站点最小价值闭环
- `Issue 9`
- `Issue 10`
- `Issue 11`

状态：仅完成抽象层骨架，详情页接入未开始

### M4：追踪站点扩展能力
- `Issue 12`
- `Issue 13`
- `Issue 14`
- `Issue 15`

状态：未开始

### M5：主页增强
- `Issue 8`

状态：未开始

---

## Issue 列表

### Issue 1：新增主页导航入口
- 目标：在主导航中引入 `Home`，并作为默认信息聚合入口
- 工作量：S
- 状态：已完成

### Issue 2：主页模块 MVP 落地
- 目标：实现最小可用 Dashboard
- 工作量：M
- 状态：已实质完成

### Issue 3：主页数据聚合与状态模型收敛
- 目标：让主页基于真实数据流渲染
- 工作量：M
- 状态：已实质完成

### Issue 4：导航配置页兼容 Home
- 目标：让现有导航配置能力支持 `Home`
- 工作量：S
- 状态：已完成

### Issue 5：主页默认启动页策略
- 目标：把主页变成默认启动页
- 工作量：S
- 状态：已完成

### Issue 6：Explore 过滤器改为两行固定布局
- 目标：消除横向滚动，改成两行布局
- 工作量：M
- 状态：已完成

### Issue 7：补齐 Explore 源类型图标资源
- 目标：为过滤器提供统一图标，并补齐共享标签模型
- 工作量：S
- 状态：已实质完成

### Issue 8：主页卡片配置能力
- 目标：支持卡片显隐、数量和顺序配置
- 工作量：M
- 状态：未开始

### Issue 9：梳理并复用现有追踪站点基础能力
- 目标：明确现有 `scrobbling/common` 能力边界
- 工作量：S
- 状态：已完成基础梳理

### Issue 10：预留追踪站点切换接口
- 目标：站点发现与详情能力通过站点无关接口暴露
- 工作量：M
- 状态：已完成接口与默认实现骨架

### Issue 11：详情页追踪信息卡一期
- 目标：在现有详情页中显示站点无关追踪信息
- 工作量：M
- 状态：未开始

### Issue 12：追踪站点本地模型与缓存表设计
- 目标：为发现页和详情页提供本地可缓存模型
- 工作量：M
- 状态：未开始

### Issue 13：追踪站点自动关联基础版
- 目标：实现本地内容与远端条目的最小可用匹配
- 工作量：L
- 状态：未开始

### Issue 14：发现页 Discover 一期
- 目标：实现站点切换、搜索、热门列表和详情跳转
- 工作量：M
- 状态：未开始

### Issue 15：追踪站点详情页一期
- 目标：展示远端详情与本地入口
- 工作量：M
- 状态：未开始

### Issue 16：追踪状态同步增强
- 目标：支持站点状态、评分、进度同步增强
- 工作量：M
- 状态：未开始

---

## 当前建议的下一步顺序

1. 详情页追踪信息卡接入 `tracking/discovery` 抽象，先形成站点无关展示入口
2. 在此基础上落地 `Discover` 一期，默认由 Bangumi 提供数据
3. 再补追踪站点本地缓存模型与详情页
4. 最后做自动关联、状态同步增强与主页配置化
