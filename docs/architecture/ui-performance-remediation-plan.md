# UI 性能与加载体验整改计划

## 目标

本计划用于跟踪 Kototoro 当前首页、浏览页、详情页与通用列表的稳定性、性能和加载体验整改进度。

原则：

- 先修确定性错误，再压缩高频性能热点，最后统一加载体验。
- 优先做低风险、高收益、可验证的改造。
- 避免把体验问题和性能问题混为一谈，但两者都要落地。

## 问题清单

### P0 稳定性与错误显示

- [x] 修复部分来源列表重复 `key` 导致的 Compose 崩溃
- [x] 修复部分来源封面请求缺失上下文导致的加载失败
- [x] 修复搜索覆盖层输入框编辑态高度和浅色大胶囊背景
- [x] 修复详情页底部工具栏浅色残留背景
- [x] 修复详情页折叠态阅读按钮露出不足
- [x] 修复详情页评分胶囊浅色模式发白
- [x] 压缩详情页标签区多行排布间距

### P1 性能热点

- [x] 首页摘要状态链路拆分，缩小重算和重组范围
- [x] 首页预览卡片模型映射去重，避免 3 次重复 `toListModelList`
- [x] 通用卡片设置订阅上提到页面级，减少 item 级 `SharedPreferences` listener
- [x] 首页卡片徽章订阅上提到页面级，移除 item 级 `observeAsState`
- [x] 浏览页来源过滤链路降噪，移除高频逐项日志
- [x] 浏览页热门项背景效果降本，压缩 GPU 热点
- [x] 浏览页来源快捷区展开态布局成本优化
- [x] 校正部分图片请求的 size/source extra 策略

### P1 加载体验连续性

- [x] 首页 loading 改为 skeleton 占位，而不是仅圆圈
- [x] 浏览页 Hero loading 改为 skeleton 占位
- [x] 浏览页来源快捷区 loading 改为 skeleton 占位
- [x] 浏览页热门列表 loading 改为 skeleton 占位
- [x] 通用内容列表 `LoadingState` 改为按 ListMode 渲染 skeleton item

### P2 次级优化

- [x] `HorizontalRailAnimatedVisibility` 继续降本
- [x] 复核浏览页 load more 触发频率
- [ ] 用真实设备/低端机复核首页和浏览页滚动体感

## 当前轮次计划

### Round 1

- [x] 建立任务文档
- [x] 浏览页过滤链路移除高频 `Log.v`
- [x] 通用列表卡片设置订阅上提
- [x] 首页卡片徽章订阅上提
- [x] 首页 loading skeleton
- [x] 浏览页 loading skeleton
- [x] 通用列表 loading skeleton
- [x] 编译或静态验证

## 进度记录

### 2026-04-25

- 建立整改跟踪文档，统一记录范围、优先级与执行顺序。
- 已确认此前已完成的 UI 修复仍保留在工作区中，后续改造在此基础上继续推进。
- 首页预览模型映射改为单轮汇总后统一建模，去掉原先 3 次重复 `toListModelList(..., GRID)`。
- 通用内容卡片新增页面级 `ContentCardUiPrefs`，列表页统一读取一次设置后下发给 grid 卡片。
- 首页封面行徽章设置改为页面级订阅，移除 item 级 `observeAsState`。
- 浏览页 `applyGroupTabFilter()` 已移除高频逐项日志输出，降低 debug 包切换 tab/filter 时的额外噪音。
- 首页、浏览页 Hero/来源区/热门区、通用列表 `LoadingState` 已替换为结构化 skeleton 占位。
- 浏览页热门列表背景模糊半径和叠层透明度已下调，并为部分封面请求补充了尺寸约束。
- 执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 首页主状态改为只保留 `HomeScreen` 实际消费字段，并把筛选状态单独拆成 `HomeFilterState`；原先未被首页 UI 消费的 favourites/source/sync 分支已从首页主 `combine` 中移除。
- 首页 Fragment 和 Compose NavGraph 都已改为订阅窄化后的 `screenState/filterState`，减少无关字段变化带来的首页重组。
- 浏览页来源快捷区已从每组一个禁滚动 `LazyVerticalGrid` 改为静态分行布局，减少展开态的子布局和子组合开销。
- 浏览页热门卡片与 tracking 横向卡片的封面请求已统一带上 `mangaExtra`、协议归一化和尺寸约束。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 发现当前机器未同步到昨晚远端提交后，已显式抓取 `refs/heads/compose`，避免再次误拉同名 `compose` tag。
- 已将本地工作区 rebase 到最新 `origin/compose`，并完成 autostash 回放产生的冲突合并。
- 首页 `HomeViewModel/HomeFragment/AppNavGraph` 已切回远端最新 `summaryState` 结构，同时保留本地预览卡片去重建模优化。
- 横向轨道动画已改成“每个 `LazyRow` 一次滚动强度采样，item 共享结果”，避免原先每个 item 独立采样滚动速度。
- 浏览页 load more 监听已补 `distinctUntilChanged()`，并改为通过 `rememberUpdatedState` 读取 loading 状态，减少重复触发。
- 首页、浏览页、发现页、追踪页、搜索页的横向轨道调用点已全部适配新的 `scrollIntensity` 参数。
- 详情页标签区、底部工具栏、评分胶囊，以及首页/列表卡片的徽章与封面请求链路，已在远端最新代码基础上重新合并验证。
- 已复核 `1d94f16b5 fix(details,feed): finish compose details migration and stabilize hero transitions` 这次详情页 compose 重构仍完整保留；本地仅在其基础上继续修改 `DetailsHeader.kt` 和 `SharedDetailsComponents.kt` 两处 UI 细节。
- 已静态确认详情页 pane 状态仍使用 `DetailsPaneState + CompactDetailsPaneAnchor` 新结构，折叠态露出高度当前为 `collapsedHeight = 96.dp`，未回退到重构前实现。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，在合并远端最新提交后编译通过。
- 发现详情页底部 pane 在 light theme 下的浅色背景感和折叠态露出高度在 rebase 后再次回归，已重新修正：
  - `DetailsPaneState.collapsedHeight` 从 `88.dp` 恢复到 `96.dp`
  - `DetailsScreen` 针对 light theme 单独下调 pane 玻璃层的 `containerAlpha / borderAlpha / shadowElevation`
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 推荐设置页 `SuggestionsSettingsScreen` 已改为通过 `observeAsState` 订阅 `KEY_SUGGESTIONS / KEY_SUGGESTIONS_WIFI_ONLY / KEY_SUGGESTIONS_DISABLED_SOURCES / KEY_SUGGESTIONS_NOTIFICATIONS`，修复“启用作品推荐”以及相关子开关写入后 UI 不刷新的问题。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 详情页紧凑 pane 顶部拖拽区已改为仅占用状态栏高度；折叠态露出高度改为按 `statusBarTopPadding + 68.dp` 动态计算，避免不同设备状态栏高度差异导致按钮区被裁切。
- 首页 Hero 轮播已改为按卡片宽度自适应紧凑排版：窄卡自动缩小封面宽度、标题字号、来源字号和“阅读进度/新章节”辅助文案字号，减少小屏文案截断。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 详情页标签区在此前极限压缩基础上，把多行纵向间距从 `2.dp` 微调回 `3.dp`，保持紧凑但不至于过挤。
- Compose 版下载对话框已补齐章节范围下拉菜单逻辑：分支选择、前 N 章、未读前 N 章的箭头按钮现在都会展开真实菜单，并回写到 `DownloadDialogViewModel`。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon`，编译通过。
- 下载确认后的反馈已统一补全：详情页内走 `SnackbarHostState.showSnackbar(..., actionLabel = 下载)`，`DialogFragment` 路径则回退到宿主 `View` 上的 Material Snackbar；两种路径都支持直接跳转下载页。
- 详情页折叠态 pane 现在把 `navigationBars` 底部 inset 一并计入 `collapsedHeight`，避免按钮区与底部手势栏重合。
- 首页 Hero -> 详情页封面转场已改用窗口坐标系同步 bounds，修复横屏下起止位置明显偏移的问题。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，编译通过。
- 详情页折叠态 pane 再次上调露出高度：基础常量从 `68.dp` 提到 `80.dp`，在手势导航设备上额外抬高按钮区，避免仍与白条区域相贴。
- Hero 转场控制器已统一把来源/目标上报的 `boundsInWindow()` 转换为详情页根布局坐标；同时首页、列表、发现页、追踪页等封面卡片的点击起点也统一改为窗口坐标，修复此前竖屏/横屏混用 `root/window` 坐标导致的整体偏移。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，编译通过。
- 详情页折叠态 pane 再小幅上调一档：基础常量从 `80.dp` 提到 `84.dp`，继续把按钮区抬离底部手势白条。
- Hero 进入详情时已禁用整页内容从右侧滑入的辅助动画；该动画会污染封面目标位置采样，导致进入时出现“往右飞走”或从左上角放大的错误轨迹。退出详情仍保留封面 Hero 回退。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，编译通过。
- Hero 转场已进一步按根因修正，而不是继续做横屏特判：
  - 来源页点击时额外记录源窗口在屏幕上的原点，进入详情时按“源窗口原点 -> 目标窗口原点 -> 详情根布局”做坐标换算，修复横屏下不同窗口原点导致的起点偏移。
  - 目标封面同步时会扣除 `composeView.translationX/Y`，避免“进入详情整页右滑”的辅助动画污染封面目标采样，修复竖屏进入时往右飞、横屏从左上角放大的问题。
- 详情页折叠态 pane 再上调一小档：基础常量从 `84.dp` 提到 `90.dp`，继续把阅读按钮抬离底部手势白条。
- 横屏主界面 chrome 逻辑已拆分：在未固定导航栏 UI 时，滚动仍可折叠顶部搜索栏，但左侧导航 rail 不再跟随内容一起滑出屏幕。
- 再次执行 `cmd /c gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，编译通过。
