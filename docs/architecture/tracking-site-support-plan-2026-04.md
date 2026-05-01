# 追踪站支持改造计划（2026-04）

## 目的

这份文档用于记录 Kototoro 追踪站支持的下一阶段改造计划，方便跨机器继续工作，也方便后续把实现进度逐项对照回填。

本轮改造的核心目标不是“再补几个接口”，而是把以下能力真正打通为可用闭环：

- `AniList`
- `MyAnimeList`
- `Simkl`

范围覆盖：

- 站点登录
- 发现页 / 浏览页
- 分类页
- 详情页
- 列表卡片信息密度
- 高清封面获取
- 简介清洗
- 本地缓存与展示链路

## 当前现状

基于 `2026-04-27` 的代码排查，当前关键结论如下。本文档此前记录的是改造起点，多个条目已经落地；后续应把它作为进度台账维护，而不是继续按旧状态执行。

### 1. Simkl 基础设施已进入主流程

`Simkl` 已经接入追踪站基础设施：

- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/common/domain/model/ScrobblerService.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/common/domain/ScrobblerRepositoryMap.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`
- `app/src/main/AndroidManifest.xml` 的 scrobbler auth host 列表

当前状态不是“体系内不存在”，而是“基础浏览、详情、绑定和同步链路已接入，剩余站点能力细化”。

已完成：

- `ScrobblerService` 增加 `SIMKL`
- `SimklRepository` / `SimklInterceptor` / `SimklScrobbler` 已接入
- `ScrobblingModule`、`ScrobblerRepositoryMap`、设置页登录入口和 OAuth callback 已接入
- 发现页分类、搜索、详情、episode 列表、推荐、状态同步、增量同步和 `removed_from_list` 删除对比已形成首批闭环

剩余重点：

- `memo` 删除与更细粒度远端注释同步策略
- 发现页分类之外的更多时间维度筛选
- 避免伪造 Simkl 不具备的 manga / 角色 / person 域

### 2. 发现页数据模型已完成首批扩展

`TrackingSiteItem` 已扩展为可承载更多站点字段：

- `primaryTitle`
- `secondaryTitle`
- `progressText`
- `updatedAtText`
- `scoreMax`

`TrackingSiteItemEntity`、`TrackingSiteCacheRepository` 与 `Migration39To40` 已同步；`DiscoverViewModel` / `DiscoverCategoryViewModel` 已开始保留标题、副标题、评分范围等字段。

剩余重点：

- `Bangumi` 排行页字段仍偏依赖页面抓取，评分、章节、更新时间颗粒度需继续验证
- 副标题目前仍是单行摘要，后续可拆成更明确的多段信息区块
- `MAL` / `Shikimori` 的“每日放送”仍主要是按日期映射季度，不是真正逐日 airing API

### 3. AniList / MAL / Simkl 详情能力已有明显推进

相关实现主要在：

- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/anilist/data/AniListRepository.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/mal/data/MALRepository.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/simkl/data/SimklRepository.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`

已完成：

- `AniList` 已接入 airing schedule、characters、voice actors、related、recommendations、高质量图片字段和 HTML 简介规整
- `MAL` 已接入 ranking / seasonal、large cover、details、reviews / forum、characters、related、recommendations
- `Simkl` 已接入 discovery categories、overview、recommendations、sync activities 与 removed list
- 主详情页追踪站补充区块已能展示角色卡片、角色弹层、声优列表与外跳站点页

剩余重点：

- 角色、声优、person 实体详情页仍需从各站点实际详情页补齐真实图片和外链
- MAL / Bangumi / Shikimori / Kitsu 的人物与角色页信息深度仍不均衡
- 站点缺少角色/person 域时必须降级展示，不能虚构字段

### 4. 简介清洗已开始统一，但仍需收口

历史问题是 `details` / `scrobbling sheet` 侧常见直接对字符串做 `sanitize()`：

- `app/src/main/kotlin/org/skepsun/kototoro/core/util/ext/String.kt`

当前 AniList 等详情已开始做 HTML 段落和换行规整，但仍需继续统一规则：

- HTML 简介优先转干净文本
- `br` / `p` 转换为合理换行
- 去除多余空白与残余标签
- 在 repository / discovery 映射阶段尽早清洗，避免 UI 层重复兜底

### 5. 用户系统入口已完成首批迁移

当前进度：

- 已完成：
  - 根设置新增独立 `用户` 分区
  - 追踪站账号入口从 `Services` 页迁移到新的 `UsersSettingsFragment`
  - 用户页新增“默认浏览追踪网站”兜底设置，发现页顶部追踪源面板异常时仍可从设置切换浏览源
  - `同步设定` 入口从 `Services` 页迁移到 `用户` 页，路径调整为 `设置 -> 用户 -> 同步设定`
  - 新增统一 `ScrobblerUserProfile` / `ScrobblerUserStats` 模型
  - 新增 `TrackingUserAccountSummaryProvider`
  - `AniList` / `MAL` / `Simkl` 已补首批账号统计摘要
  - 其他站点先回退为“昵称 + 登录状态”，后续逐站补统计

- 尚未完成：
  - `Bangumi` / `Shikimori` / `Kitsu` / `MangaUpdates` 的统计深挖
  - 用户页内的更多能力入口，例如用户评论、列表跳转、远端收藏/历史概览
  - 把“用户系统”继续扩展成跨站统一资料页，而不只是账号列表

### 6. 下一步计划：角色 / 声优 / Person 实体详情增强

用户给出的当前方向是“改善角色、声优、person 实体详情页，正确读取各个网站的网页实际内容和图片”。建议按低风险顺序推进：

1. 不先扩 Room 表结构，优先从已有追踪站详情缓存动态解析角色图、声优头像、角色页 / person 页外链，避免为首批展示能力引入 migration。
2. 统一实体关系卡片的数据来源：角色、声优、person 页面都通过 `TrackingSiteItemDetails.CharacterInfo` / `PersonInfo` 获取真实图片、职责、声优列表和 URL。
3. 逐站补齐仓库层字段映射，优先使用站点实际详情页或官方 API 已提供的高清字段；缺字段时优雅降级，不写站点特化 UI 分支。
4. 第一批重点站点顺序：`AniList`、`MAL`、`Bangumi`。`Simkl` 仅处理其真实支持的作品域，不伪造角色/person。
5. 验证路径优先覆盖：从作品详情进入角色卡片、从角色进入实体详情、从声优 / person 进入实体详情、外链按钮打开站点真实页面、图片加载成功。

本次推进记录：

- 已在 `DetailsViewModel` 侧增强实体详情动态解析：角色实体继续从关联作品的追踪站详情回补角色图、职责、声优列表和角色页 URL。
- 已新增 person / 声优头像回补策略：Person 实体通过其配音角色所在作品详情匹配 `PersonInfo`，优先使用站点实际提供的 `avatarUrl`。
- 暂不新增实体图谱图片字段和 Room migration，保持 KISS/YAGNI；如果后续需要离线展示或跨进程稳定缓存，再单独设计实体展示元数据表。

## 改造目标

完成后应达到以下状态：

1. `AniList`、`MAL`、`Simkl` 三个源都支持登录。
2. 三个源都能作为追踪站浏览源出现在发现页与分类页。
3. 三个源都尽量提供“每日放送 / 每日更新”入口。
4. 列表卡片可展示至少以下信息中的合理子集：
   - 主标题
   - 原名 / 译名
   - 评分
   - 状态
   - 已更新章节 / 集数
   - 每日放送信息
5. 详情页可稳定展示：
   - 高清主封面
   - recommendations
   - related works
   - creators / staff
   - characters
6. 简介显示不再残留原始 HTML 标签。
7. 缓存结构与 UI 结构保持站点无关，不为单站点写死分支。

## 分阶段计划

### 阶段 1：补齐 Simkl 基础设施

目标：

- 让 `Simkl` 正式成为 Kototoro 的追踪源之一

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - `ScrobblerService` 增加 `SIMKL`
  - 新增 `SimklRepository` / `SimklInterceptor` / `SimklScrobbler`
  - 接入 `ScrobblerRepositoryMap`
  - 接入 `ScrobblingModule` 的 storage / `OkHttpClient` / repository provider / scrobbler set
  - `ScrobblerConfigActivity` 与 `AndroidManifest.xml` 增加 `simkl-auth` 回调 host
  - 设置页已增加 `Simkl` 登录入口
  - OAuth code -> token -> `ScrobblerStorage` 保存链路已接通
  - `DefaultTrackingSiteDiscoveryService` 已接入 Simkl 发现页分类与列表拉取
  - 已基于官方文档补齐 Simkl 文本搜索接口
  - 已基于官方文档补齐 Simkl 详情接口与 episode 列表读取
  - 已基于官方文档补齐 Simkl `watchlist` / `history` / `ratings` / `sync/all-items` 写链路
  - 已基于官方 `sync/activities` + `date_from` 改为增量同步，并补 `removed_from_list` 的 IDs-only 删除对比
  - Simkl 已重新开放手动绑定、自动建联和状态同步入口

- 尚未完成：
  - `memo` 删除与更细粒度的远端注释同步策略
  - 发现页分类之外的更多列表形态与时间维度筛选

主要改动点：

- `ScrobblerService` 增加 `SIMKL`
- 新增 `SimklRepository`
- 接入 `ScrobblerRepositoryMap`
- 增加对应 `OkHttpClient` / storage / Hilt provider
- 增加字符串、图标、设置项
- 在 `ScrobblerConfigActivity` 和 `AndroidManifest.xml` 中补 `simkl-auth`

参考实现：

- `E:/kototoro_demo/Dartotsu/lib/Api/Simkl/Login.dart`
- `E:/kototoro_demo/Dartotsu/lib/Api/Simkl/Simkl.dart`
- `E:/kototoro_demo/Dartotsu/lib/Api/Simkl/SimklService.dart`

交付标准：

- 可以在 Kototoro 中看到 Simkl 登录入口
- OAuth 回调完成后可拿到 token 并保存
- Simkl 可以作为“可浏览 + 可搜索 + 可查看详情 + 可绑定同步”的追踪站进入主流程

### 阶段 2：扩展发现页数据模型

目标：

- 让“浏览页卡片能显示更多信息”成为模型层能力，而不是 UI 临时拼接

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - `TrackingSiteItem` 已扩展 `primaryTitle`、`secondaryTitle`、`progressText`、`updatedAtText`、`scoreMax`
  - `TrackingSiteCacheRepository` 与 `tracking_site_items` 表结构已同步扩展
  - 已新增 `Migration39To40`
  - `DiscoverViewModel` / `DiscoverCategoryViewModel` 已改为保留并透传标题、副标题、评分范围
  - 浏览卡片右上角评分徽标已支持 `value/max` 显示
  - 网格卡片与列表卡片已开始显示追踪站副标题
  - `AniList` / `MAL` / `Kitsu` / `Shikimori` / `Simkl` / `MangaUpdates` 的发现列表已补第一批真实字段映射：
    - 原文主标题
    - 译名副标题
    - 章节 / 集数统计
    - 更新时间或播出日期
    - 站点原生评分与评分上限

- 尚未完成：
  - `Bangumi` 排行页的真实评分 / 更新时间 / 章节统计还主要依赖页面抓取，字段颗粒度仍偏弱
  - 副标题目前仍压缩为单行文本，后续可继续拆成更明确的多段信息区块
  - “每日放送独立页面” 已有独立入口，且目前已形成分层能力：
    - `Bangumi calendar` 已支持真实按日期切换
    - `AniList airing schedule` 已支持真实按日期查询与独立页日期切换
    - `Simkl anime/tv airing` 已支持把所选日期透传为真实 `date` 请求参数
    - `MAL seasonal` 与 `Shikimori seasonal` 已支持把所选日期映射到对应季度并刷新列表
    - 但 `MAL` / `Shikimori` 当前仍是“按日期映射季度”，不是真正的逐日 airing API
  - 详情页评论按钮已支持内联阅读与站点外跳，但真正的“站内发评论 / 发长评 API 写入”仍需逐站单独深挖

主要改动点：

- 扩展 `TrackingSiteItem`
- 扩展 `TrackingSiteCacheRepository`
- 必要时扩展 `tracking_site_items` 表结构并补 migration
- 调整 `DiscoverViewModel`
- 调整 `DiscoverCategoryViewModel`
- 调整发现页卡片映射与展示

建议新增或统一的字段：

- `secondaryTitle`
- `originalTitle`
- `translatedTitle`
- `statusText`
- `progressText`
- `airingText`
- `coverUrl`
- `backdropUrl`
- `score`

交付标准：

- 数据不会在 `TrackingSiteItem -> Content` 这一步丢失
- 浏览页 / 分类页卡片能够消费新增字段

### 阶段 3：增强 AniList 接入

目标：

- 把 AniList 从“可浏览”提升到“信息完整且图片质量够用”

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - 发现页分类已新增 `AniList airing schedule`
  - 顶部“每日放送”入口已可优先跳转到 `AniList airing schedule`
  - `AniListRepository` 已新增基于 `airingSchedules` 的按日期 GraphQL 查询
  - 独立页日期选择已可真实驱动 `AniList` 每日放送列表
  - `AniList airing` 已接入专用空态与顶部日期副标题
  - `AniList airing` 卡片已开始展示本地放送时刻，而不只是日期
  - `AniList` 发现分类已继续补齐首批高价值列表：
    - `Trending Movies`
    - `Top Rated Series`
    - `Most Favourited Series`
    - `Trending Manhwa`
    - `Trending Novels`
  - `AniList` 分类页切换按钮已可在更多 anime / manga 官方列表之间直接跳转
  - `AniList` 详情页的关联作品 / 推荐作品封面已优先使用 `extraLarge/large`
  - `AniList` 详情页已补角色列表与声优数据读取，角色图 / 声优头像优先使用更高质量图片
  - 追踪站详情弹窗的简介已改为先解析 HTML 再显示，`AniList` 简介也补了首批段落/换行规范化
  - 主详情页的追踪站补充区块已开始展示 `AniList` 角色卡片，并显示 `role + CV`
  - 追踪站补充区块中的角色卡片已支持轻量详情弹层，进入后可查看完整声优列表
  - 角色轻量详情弹层已补站点角色页外跳按钮，形成“卡片摘要 -> 弹层明细 -> 站点角色页”链路
  - 角色卡片信息层级已细化为“标题 / 角色 / CV”，并增加明确的站外打开提示

- 尚未完成：
  - 详情页角色 / 推荐 / 关联封面仍有继续提高清晰度空间
  - 简介清洗与更丰富的详情扩展区块仍待继续补齐

主要改动点：

- 在 `AniListRepository` 中补每日放送 / airing schedule 数据
- 详情查询补强：
  - 高清封面
  - characters
  - staff / creators
  - recommendations
  - relations
  - 更完整标题字段
- 简介统一清洗

重点问题：

- 当前推荐 / 关联 / 角色图很多仍然是 `medium`
- 应优先改为 `large` / `extraLarge` / 更合适字段

交付标准：

- AniList 分类里可见每日放送或等效日历入口
- 详情页角色 / 推荐 / 关联封面质量明显提升

### 阶段 4：增强 MAL 接入

目标：

- 补齐 MAL 浏览与详情的信息深度

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - ranking / seasonal 浏览已接入
  - 详情页已补 large cover、基础详情字段、reviews / forum 入口
  - characters、related works、recommendations 已接入首批网页解析

- 尚未完成：
  - 角色详情页 / people 页的真实正文、头像和外链仍需逐页验证
  - “每日放送 / 当日更新”仍不是 MAL 官方逐日 airing API
  - MAL 网页结构变化风险高，解析应保持小函数和可降级策略

后续改动点：

- 为 MAL 增加“每日放送 / 当日更新”的发现入口
- 扩展详情字段：
  - 原名 / 标题信息
  - 评分
  - 章节 / 集数
  - 作者
  - 推荐作品
  - 相关作品
- 评估角色 / 创作者 / 推荐区块是否需要额外数据源或补抓逻辑
- 统一简介清洗

参考点：

- `E:/kototoro_demo/Dartotsu/lib/Api/MyAnimeList/*`

交付标准：

- MAL 不再只停留在 ranking / seasonal 的浅层浏览
- 详情页信息密度与 AniList 基本对齐
- 角色和 people 实体页能显示 MAL 实际网页提供的图片和外链；解析失败时不阻塞作品详情

### 阶段 5：完善 Simkl 发现页与详情页

目标：

- 把 Simkl 从“基础可用”继续扩展到“浏览、详情、同步体验更完整”

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - 登录、搜索、发现分类、详情、episode 列表、overview、recommendations 已进入主流程
  - `sync/activities` 增量同步和 `removed_from_list` 删除对比已接入

- 尚未完成：
  - `memo` 删除与远端注释同步策略
  - 更多列表形态和时间维度筛选
  - Simkl 不提供稳定角色/person 域时，不应为了统一 UI 伪造角色或声优实体

后续改动点：

- 发现页接入：
  - trending
  - premieres
  - airing
- 详情页接入：
  - `poster`
  - `fanart`
  - `overview`
  - `users_recommendations`
  - 评分 / 状态 / 播出信息
- 统一映射到 `TrackingSiteItem` / `TrackingSiteItemDetails`

参考实现：

- `E:/kototoro_demo/Dartotsu/lib/Api/Simkl/SimklQueries/GetAnimeMangaListData.dart`
- `E:/kototoro_demo/Dartotsu/lib/Api/Simkl/Data/Media.dart`

注意：

- Simkl 的数据域偏 anime / shows / movies，不应伪造其不具备的 manga 语义
- UI 展示需要按站点能力做条件化

交付标准：

- Simkl 可作为实际可用的浏览源进入主流程
- Simkl 缺失角色/person 数据时展示为空或隐藏对应区块，而不是展示错误占位数据

### 阶段 6：统一详情页与浏览页展示

目标：

- 真正把新增字段“显示出来”，而不是只停在仓库层

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - 浏览卡片已开始消费扩展后的标题、副标题、评分范围
  - 详情页追踪站补充区块已支持角色、关联、推荐、额外区块
  - 角色卡片和角色弹层已能展示角色图、职责、声优列表和站点外链
  - 本次新增：实体详情页能从关联作品追踪站详情动态回补角色图、角色职责、声优列表、角色页 URL 和 person / 声优头像

- 尚未完成：
  - Person 实体页还需要补正文、代表作、更多站点外链等信息
  - 角色 / person 页仍主要依赖已缓存作品详情；如果缓存缺失，需要逐站设计安全的详情补抓策略

后续改动点：

- `discover` 卡片展示逻辑
- `details` 头部信息展示逻辑
- `supplemental sections` 映射
- recommendations / related / characters / creators 列表项封面请求

关注文件：

- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/*`
- `app/src/main/kotlin/org/skepsun/kototoro/discover/ui/compose/*`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/DetailsViewModel.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsHeader.kt`

交付标准：

- 浏览页卡片信息更完整
- 详情页补充区块真正使用高清图
- 各站点字段缺失时展示能优雅降级
- 从作品、角色、声优三个入口进入实体详情时，图片和外链来源一致且可解释

### 阶段 7：简介清洗与统一图片策略

目标：

- 解决“简介脏”和“列表图清晰、详情区反而糊”的问题

当前进度（更新于 `2026-04-27`）：

- 已完成：
  - AniList 简介已开始 HTML 段落 / 换行规整
  - 追踪站详情弹窗已开始使用清洗后的简介
  - 角色、声优、关联作品、推荐作品已优先使用详情接口提供的较高质量图片字段

- 尚未完成：
  - 清洗函数尚未完全收口为站点无关工具
  - MAL / Bangumi 等网页解析来源仍需按实际 HTML 验证残留标签、实体转义和图片 URL 归一化

后续改动点：

- 抽离统一的 tracking 描述清洗函数
- 在 repository / discovery 映射阶段就完成 HTML 清洗
- 统一封面 URL 归一化与优先级策略

建议规则：

- HTML 简介优先转干净文本
- `br/p` 转换为合理换行
- 去除多余空白与残余标签
- 推荐 / 角色 / 创作者列表优先使用详情接口中的高清图字段

交付标准：

- 简介不再出现明显 HTML 残留
- 详情补充区块封面质量与详情主图策略一致

## 建议实施顺序

旧的阶段 1 到阶段 3 已基本完成，后续建议按下面顺序推进，降低回归风险：

1. 阶段 6：先把角色 / 声优 / person 实体详情的数据展示链路补稳。
2. 阶段 4：补 MAL 角色页 / people 页真实内容、图片和外链解析。
3. 阶段 7：收口简介清洗与图片 URL 归一化。
4. 阶段 5：继续完善 Simkl 真实支持的作品域能力，明确不支持角色/person 时的降级。

这样做的原因：

- 先让实体详情页消费现有数据，避免过早扩库表
- 再补站点仓库层字段，能直接看到 UI 收益
- 最后统一清洗和图片策略，减少重复解析逻辑

## 验证清单

每阶段至少完成以下验证：

- `./gradlew :app:compileDebugKotlin --no-daemon`

涉及登录时额外验证：

- AniList 登录
- MAL 登录
- Simkl 登录
- 回调 URI 是否正确回到 `ScrobblerConfigActivity`

涉及浏览与详情时额外验证：

- 发现页首页加载
- 分类页分页
- 详情页推荐区块
- 详情页角色区块
- 详情页 creators / staff 区块
- 简介无 HTML 残留
- 高清图实际加载成功

## 进度记录建议

建议后续直接在本文档末尾追加：

- 已完成阶段
- 当前阻塞点
- 待验证项
- 关键接口差异

这样换机器继续时，不需要重新做上下文恢复。
