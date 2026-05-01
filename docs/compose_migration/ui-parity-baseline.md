# Compose UI 细节对齐基线

> 日期：2026-04-21
>
> 目的：为后续 Compose UI 回补提供统一基线，避免继续以“重做一版新 UI”的方式替代旧版多年打磨的成熟实现。
>
> 说明：本文件基于当前仓库与同级 `../Kototoro_devel` 的代码结构对比整理，重点是“应当对齐的细节”，不是截图验收记录。

---

## 1. 结论先行

当前 Compose 化已经完成了主要页面和主路由迁移，但不少页面把旧版 UI 中真正成熟的细节一起替换掉了。后续回补应遵循下面这条硬规则：

- `新增元素、控件、特效` 可以保留。
- `原本就存在的信息层级、控件位置、间距节奏、列表卡片形态、标签/徽章/进度表达、空态/错误态结构、滚动联动方式`，应优先对齐 compose 前实现。
- Compose 不应把旧版“成熟细节”抽象成更泛化但信息密度更低的通用卡片、通用菜单、通用 hero、通用 settings card。

一句话概括：`Compose 是渲染技术升级，不是视觉与交互基线重置。`

---

## 2. 本次对比使用的基线文件

### 旧版基线（`Kototoro_devel`）

- 主壳：`app/src/main/res/layout/activity_main.xml`
- 搜索栏过滤器：`app/src/main/res/layout/layout_searchbar_filters.xml`
- 首页：`app/src/main/res/layout/fragment_home.xml`
- 收藏容器：`app/src/main/res/layout/fragment_favourites_container.xml`
- 通用列表：`app/src/main/kotlin/org/skepsun/kototoro/list/ui/ContentListFragment.kt`
- 网格卡片：`app/src/main/res/layout/item_content_grid.xml`
- 紧凑列表项：`app/src/main/res/layout/item_content_list.xml`
- 详情页：`app/src/main/res/layout/activity_details.xml`
- 详情元信息表：`app/src/main/res/layout/layout_details_table.xml`
- 发现页：`app/src/main/kotlin/org/skepsun/kototoro/discover/ui/DiscoverFragment.kt`
- 发现轮播行：`app/src/main/res/layout/item_discover_carousel.xml`
- 来源卡片：`app/src/main/res/layout/item_explore_source_grid.xml`
- 设置项：`app/src/main/res/layout/item_preference.xml`
- 搜索建议面板：`app/src/main/res/layout/fragment_search_suggestion.xml`

### 当前 Compose 实现（`Kototoro`）

- 主壳：`app/src/main/kotlin/org/skepsun/kototoro/main/ui/MainActivity.kt`
- Compose 主壳：`app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroApp.kt`
- 顶栏：`app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroTopBar.kt`
- 首页：`app/src/main/kotlin/org/skepsun/kototoro/home/ui/compose/HomeScreen.kt`
- 收藏容器：`app/src/main/kotlin/org/skepsun/kototoro/favourites/ui/compose/FavoritesHostScreen.kt`
- 通用列表：`app/src/main/kotlin/org/skepsun/kototoro/list/ui/compose/KototoroContentListScreen.kt`
- 通用卡片：`app/src/main/kotlin/org/skepsun/kototoro/list/ui/compose/KototoroContentCard.kt`
- 详情页：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsScreen.kt`
- 详情头部：`app/src/main/kotlin/org/skepsun/kototoro/details/ui/compose/DetailsHeader.kt`
- Explore 来源页：`app/src/main/kotlin/org/skepsun/kototoro/explore/ui/compose/ExploreSourcesScreen.kt`
- 设置页：`app/src/main/kotlin/org/skepsun/kototoro/settings/compose/SettingsRootScreen.kt`
- 设置通用组件：`app/src/main/kotlin/org/skepsun/kototoro/settings/compose/SettingsPreferenceComponents.kt`

---

## 3. 对齐判断标准

下面这些属于“必须对齐”的 UI 细节，不应因为换成 Compose 就被重做：

- 信息顺序：标题、副标题、统计、标签、来源、进度、操作入口的先后关系。
- 版式结构：一行几列、是否为表格式、是否为 rail、是否为顶部卡片、是否为底部按钮区。
- 间距节奏：外边距、内边距、标题与内容间距、分组之间的留白。
- 控件密度：chip 大小、按钮样式、图标尺寸、列表项高度、卡片圆角。
- 状态表达：徽章、进度、来源角标、收藏/本地状态、空态 CTA、错误态重试。
- 交互节奏：搜索展开方式、滚动时 app bar / bottom nav 联动、tab 与 pager 的关系、更多按钮位置。

下面这些属于“可以创新，但不能破坏旧基线”的项目：

- Glass / haze / hero / 转场特效
- 新增的 tracking 绑定卡片
- 新增的 display options 面板
- 新增的 hover/drag pane、动画、动态透明度

判断原则：

- `新增效果只能叠加，不能替代旧结构。`
- `新组件如果降低了旧版信息密度或扫描效率，就算回退。`

---

## 4. 全局回补原则

### 4.1 保持旧版的信息密度

旧版 UI 的价值不在“XML”，而在于每个页面的信息压缩和扫描效率已经稳定：

- 首页一屏内同时给出历史、更新、推荐、快捷入口、同步状态、来源概览。
- 详情页在头部之后马上给出结构化元信息表，而不是散成抽象信息卡。
- 列表卡片把来源、本地状态、收藏状态、阅读进度、badge 都压在封面及其邻近区域。

Compose 回补时，优先恢复这些密度，不要为了“更现代”把它们拆散或藏进更多菜单。

### 4.2 先恢复布局基线，再讨论视觉增强

正确顺序应该是：

1. 先把旧版布局关系、间距、控件位置对齐。
2. 再把 Compose 新增视觉效果叠加上去。

不应反过来先做 glass / hero / carousel，再用这些新效果掩盖结构偏差。

### 4.3 通用组件必须服从页面基线

当前仓库已经有不少通用 Compose 卡片/设置项/hero 组件，这本身没问题；问题在于：

- 一旦通用组件与旧版页面基线冲突，应优先调整通用组件。
- 不要为了复用，把本来是“表格”、“条目”、“轻量 rail”、“边角 badge”的东西统一改成“大圆角信息卡”。

---

## 5. 分页面基线

## 5.1 主壳 / 顶栏 / 搜索

### 旧版基线

旧版主壳由 `activity_main.xml` 定义了非常明确的层级：

- `AppBarLayout + SearchBar`
- 搜索建议面板 `SearchView + RecyclerView`
- 顶栏右侧三枚紧凑过滤控件：语言预设、内容类型 swipe pill、来源筛选
- 底部滑动导航栏
- 锚定在底栏上方的 `Continue` 扩展 FAB

成熟细节包括：

- 搜索栏本身就是主 chrome，而不是额外包一层存在感很强的装饰容器。
- 过滤器是顶栏一等公民，不应被更多菜单吞掉。
- 搜索建议有独立背景面板，不是普通 dropdown。
- app bar 与 bottom nav 会随滚动协同隐藏/出现。
- 继续阅读入口固定锚在底栏上方，而不是到处漂浮。

### 当前应对齐点

- `KototoroTopBar` 目前保留了过滤器顺序，但外层 glass 容器、display options 浮层、搜索展开语义应继续服从旧版的紧凑密度。
- `KototoroApp` 当前没有把旧版 `Continue` FAB 对齐回来，`isResumeEnabled` / `onResumeClick` 已传入但主壳未渲染对应入口。
- 搜索建议面板应继续保持“独立内容面板”感，而不是普通菜单感。
- 顶栏与底栏的滚动联动要按旧版节奏工作，不能只做简单位移。

### 回补要求

- 保留 Compose 顶栏，但继续以“单行搜索主栏 + 紧凑过滤器 + 独立建议面板”为视觉基线。
- 恢复底栏上方的继续阅读入口，至少保留旧版的固定位置与可达性。
- 不把现有过滤器迁回二级菜单。

---

## 5.2 首页

### 旧版基线

`fragment_home.xml` 的页面结构不是一个泛化 dashboard，而是 4 块职责明确的成熟卡片：

- 历史 / 更新 / 推荐 三合一卡片
- 快捷入口卡片
- 同步状态卡片
- 来源概览卡片

关键细节：

- 首页整体外边距非常克制：`2dp / 4dp / 24dp` 级别。
- 三合一卡片内每一段都采用：`标题 + 数量 + 右侧 borderless More + 横向封面 rail`。
- 快捷入口是 `2 列按钮矩阵`，而不是图标宫格。
- 同步状态卡片保留状态文案、最近同步时间、备份/恢复/设置入口。
- 来源概览卡片保留来源分类计数和右下角 `Manage` 入口。

### 当前应对齐点

`HomeScreen.kt` 目前只保留了：

- highlight sections
- quick actions

已丢失：

- 同步状态卡片
- 来源概览卡片
- 快捷入口按钮的旧版矩阵结构与样式基线

### 回补要求

- 首页必须恢复为旧版四块式信息结构，Compose 新增效果只能包裹这四块，不应裁剪其中两块。
- 三合一卡片继续保持旧版 header 结构：标题、数量、右侧 `More`。
- 快捷入口应优先对齐旧版按钮矩阵的密度和按钮风格，而不是继续抽象成统一 action chip/card。

---

## 5.3 通用列表与内容卡片

### 旧版基线

旧版 `ContentListFragment` + `item_content_grid.xml` + `item_content_list.xml` 建立了一套很稳定的内容卡片规则：

- 网格卡片：
  - 封面下方是两行标题
  - 左下角是来源/状态信息条
  - 右上角是 badge
  - 右下角是阅读进度
  - 外边距和内边距非常小，目的是提高单位面积信息量
- 紧凑列表：
  - 使用轻量行式布局
  - 小封面 + 标题 + 副标题
  - 保持列表扫描速度，不引入海报化大卡
- 列表页本身：
  - 快速滚动、分页、间距装饰、系统栏 inset 处理已经稳定
  - 与顶栏过滤器有明确分工

### 当前应对齐点

- `KototoroContentCardGrid` 已经回到“左下来源条 + 右上 badge + 右下进度”的方向，这是正确的。
- 但 `KototoroContentCardList` 仍然更接近“海报型条目”，与旧版紧凑列表的轻量行式结构不一致。
- `KototoroContentCardDetailedList` 也更偏大海报卡，而不是旧版细节型列表。

### 回补要求

- 网格卡继续沿着旧版的角标/进度/来源贴边表达收口，不要再把它们改成漂浮装饰。
- `ListMode.LIST` 要优先恢复旧版轻量列表扫描感，不应继续使用大海报式条目。
- `ListMode.DETAILED_LIST` 需要对照旧版详细列表的文本密度和层级，而不是简单在海报右侧堆字。

---

## 5.4 收藏容器

### 旧版基线

`fragment_favourites_container.xml` + `FavouritesContainerFragment.kt` 的成熟点主要在容器行为：

- 顶部是 `Scrollable TabLayout + ViewPager2`
- 空态有明确管理入口
- 过滤器与主搜索栏集成
- 对 app bar snap 行为做了特意处理，保证滚动节奏自然

### 当前应对齐点

- `FavoritesHostScreen.kt` 已保留 tab + pager 结构，这点是对的。
- 但空态只剩说明文案，没有旧版 `Manage` CTA。
- Compose 版本需要继续对齐旧版 tab 密度、空态结构与容器行为，而不是只保留“有 tab”。

### 回补要求

- 恢复收藏空态中的管理入口。
- Tab 的视觉密度、边缘留白、滚动体验应继续参考旧版 `TabLayout`。
- 收藏容器继续视为“带分页的列表壳”，不要改造成新的 dashboard。

---

## 5.5 Explore / Discover / 来源浏览

### 旧版基线

旧版这里其实有两套非常明确的成熟样式：

- 来源浏览：
  - favicon/图标居中
  - 标题单行居中
  - 可选的小来源类型 chip
  - 更接近“工具入口格子”，不是信息卡
- Discover：
  - 分类标题 + `More`
  - 下方横向 carousel
  - tracking site 列表卡是轻量信息卡，重点是封面、标题、评分、副标题、摘要、站点

### 当前应对齐点

- `KototoroSourceCard` 现在更像通用 Material Card，工具入口属性被削弱。
- Discover/Explore 现在大量使用 hero/backdrop/carousel 视觉语言，容易盖过旧版“分类浏览页”的实用信息结构。

### 回补要求

- 来源卡要优先对齐旧版“入口格子”语义，而不是继续卡片化。
- Discover 分类行继续保持旧版“标题 + More + 横向内容”的清晰结构。
- tracking/discover 新增 hero 可以保留，但不能取代旧版轻量信息卡的可扫描性。

---

## 5.6 详情页

### 旧版基线

旧版 `activity_details.xml` + `layout_details_table.xml` 的成熟度很高，核心不是视觉，而是结构：

- 上半区：panorama / cover / title / subtitle / 收藏 chip
- 紧接着是 `标签名 -> 值` 的结构化元信息表
- 然后是 description
- 然后是 tags
- 然后是 tracking / related / bottom sheet 等后续区块

关键细节：

- 元信息是“表”，不是“卡片矩阵”。
- 信息扫描顺序非常稳定：source、author、translation、rating、state、chapters、local、progress。
- 收藏入口是 chip，不是普通图标按钮。
- 详情的头部塌缩、toolbar 透明度、cover 转场都围绕这套结构服务。

### 当前应对齐点

`DetailsHeader.kt` 当前已经恢复了不少内容，但仍有两个明显偏差：

- 元信息区被改写成了两列图标信息块，弱化了旧版的表格扫描效率。
- 收藏、翻译等操作已经偏向 icon cluster，而不是以旧版主操作布局为基线。

### 回补要求

- 详情页元信息区应优先回到旧版“表格/行式元信息”结构，而不是继续打磨通用 metadata card。
- 收藏主操作的语义和位置应对齐旧版 chip 入口。
- 新增 tracking 绑定卡片、翻译能力、pane 动画都可以保留，但只能附着在旧版详情信息结构之上。

---

## 5.7 设置页

### 旧版基线

旧版设置项 `item_preference.xml` 的特点很明确：

- 扁平、轻量、系统化
- 列表行高稳定
- 标题/摘要两层信息清楚
- 依赖点击反馈和分组标题，而不是大面积卡片包装

### 当前应对齐点

- `SettingsRootScreen.kt` 当前大量使用 hero card、section card。
- `SettingsPreferenceComponents.kt` 也更偏“Compose 组件展示”，而不是旧版偏好项行式结构。

### 回补要求

- 设置系统的视觉应优先回到“偏好列表”而不是“设置 dashboard”。
- 可以保留 Compose 的实现方式，但不应继续扩大卡片化与 hero 化。
- 根设置页尤其应谨慎，旧版的可扫描性比当前 card stack 更强。

---

## 5.8 搜索建议与搜索结果外围

### 旧版基线

旧版 `fragment_search_suggestion.xml` 明确把搜索建议视为一个独立内容层：

- 单独背景
- 独立滚动容器
- 与搜索栏有明确主从关系

### 当前应对齐点

- Compose `DockedSearchBar` 路径可以保留，但建议列表必须继续保持“内容面板”属性。
- 搜索建议不应看起来像顶栏更多菜单的变体。

### 回补要求

- 保持建议列表的独立背景、底部 inset、安全滚动范围与内容层级。
- 搜索结果外围的 display options、筛选交互也应服从旧版“搜索是主任务、菜单是辅助手段”的关系。

---

## 6. 当前已经对齐或已部分回补的项目

这些不需要重复返工，但后续修改时要继续守住：

- 网格卡来源角标已回到贴边 badge 逻辑。
- Compose 来源 favicon 行为已经向旧版 `FaviconView` 靠拢。
- 首页三合一卡片的内部水平 padding 已回补。
- 顶栏过滤器顺序基本保留了旧版的语言预设、内容类型、来源筛选顺序。
- 详情页翻译入口的显示条件和提示语义已经更接近成熟状态。

---

## 7. 当前最值得优先回补的缺口

按收益排序，建议先处理下面几项：

1. 首页恢复旧版四块结构，补回同步状态卡与来源概览卡。
2. 列表 `LIST / DETAILED_LIST` 形态回归旧版轻量行式与详细列表基线。
3. 详情页元信息区从信息块矩阵回归旧版标签-值表结构。
4. 主壳恢复底栏上方的继续阅读入口。
5. 收藏空态恢复管理 CTA。
6. 来源浏览卡从通用 Material Card 回归旧版“入口格子”。
7. 设置根页减少 card 化，回到偏好列表扫描节奏。

---

## 8. 执行边界

后续任何 UI 回补，如果遇到“旧版结构 vs 当前 Compose 通用组件”冲突，按下面优先级裁决：

1. 旧版成熟页面结构
2. 当前功能正确性
3. Compose 通用组件复用
4. 新增视觉特效

也就是说：

- 可以为了对齐旧版，拆掉不合适的通用 Compose 卡片。
- 不可以为了保住通用组件，而继续偏离旧版成熟 UI。

---

## 9. 后续文档分工建议

本文件只定义“应该对齐到什么程度”。

后续如果开始逐页回补，建议再补两类文档：

- `ui-parity-todo.md`：按页面列具体回补任务。
- `ui-parity-checklist.md`：每页验收时逐条勾选是否对齐旧版结构、间距和交互。

