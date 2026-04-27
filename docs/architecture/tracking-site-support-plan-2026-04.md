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

基于 `2026-04-27` 的代码排查，当前关键结论如下：

### 1. Simkl 尚未进入 Kototoro 的追踪站基础设施

当前 `Simkl` 不在：

- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/common/domain/model/ScrobblerService.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/common/domain/ScrobblerRepositoryMap.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`
- `app/src/main/AndroidManifest.xml` 的 scrobbler auth host 列表

这意味着它现在不是“功能不完整”，而是“体系内不存在”。

### 2. 发现页卡片当前是轻量模型，信息在中途丢失

当前发现页主模型是：

- `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/domain/TrackingDiscoveryModels.kt`

其中 `TrackingSiteItem` 只有：

- 标题
- 副标题 / 别名
- 封面
- 评分
- URL

而 `DiscoverViewModel` / `DiscoverCategoryViewModel` 在转换成 `Content` 时，并没有保留你希望展示的更多字段，例如：

- 每日放送
- 已更新章节 / 集数
- 原名 / 译名双标题
- 更完整的副信息

因此即使后端接口能拿到数据，卡片层也显示不出来。

### 3. AniList / MAL 详情已接通基础能力，但数据颗粒度不足

相关实现主要在：

- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/anilist/data/AniListRepository.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/scrobbling/mal/data/MALRepository.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/tracking/discovery/data/DefaultTrackingSiteDiscoveryService.kt`

现存问题：

- `AniList` 推荐、关联、角色等区块很多地方仍在使用 `medium` 图
- `MAL` 详情字段偏少，推荐 / 人物 / 更高质量图片没有完整接入
- `discover` 列表层对这些富数据没有统一映射

### 4. 简介清洗不足

当前 `details` / `scrobbling sheet` 侧常见是直接对字符串做 `sanitize()`：

- `app/src/main/kotlin/org/skepsun/kototoro/core/util/ext/String.kt`

这个逻辑只是在处理异常替代字符，不负责：

- 去掉 `<p>`
- 去掉 `<br/>`
- 规整多余空行
- 将 HTML 简介转成更干净的纯文本或可控富文本

所以会出现简介残留 HTML 标记的问题。

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

### 阶段 2：扩展发现页数据模型

目标：

- 让“浏览页卡片能显示更多信息”成为模型层能力，而不是 UI 临时拼接

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

主要改动点：

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

### 阶段 5：新增 Simkl 发现页与详情页

目标：

- 把 Simkl 从“能登录”扩展到“能浏览、能看详情、能看推荐”

主要改动点：

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

### 阶段 6：统一详情页与浏览页展示

目标：

- 真正把新增字段“显示出来”，而不是只停在仓库层

主要改动点：

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

### 阶段 7：简介清洗与统一图片策略

目标：

- 解决“简介脏”和“列表图清晰、详情区反而糊”的问题

主要改动点：

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

建议按下面顺序落地，降低回归风险：

1. 阶段 1：Simkl 基础设施
2. 阶段 2：发现页数据模型扩展
3. 阶段 3：AniList 增强
4. 阶段 4：MAL 增强
5. 阶段 5：Simkl 浏览与详情
6. 阶段 6：统一 UI 展示
7. 阶段 7：简介清洗与图片策略收尾

这样做的原因：

- 先把站点能力纳入统一抽象
- 再扩数据模型
- 最后补 UI 和清洗，避免重复返工

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
