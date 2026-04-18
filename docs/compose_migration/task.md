# Compose 迁移：后续计划

> 当前状态见 `status-snapshot.md`。历史决策见 `decision-log.md`。
> 本文件只列出**尚未完成、需要推进的工作**。

---

## 迁移优先级原则

- 按"桥接深度"推进，而非"是否用了 Compose"
- 目标：逐组件从 L1 → L2 → L3，L4 仅在共享层条件成熟时讨论
- 不强推一次性全仓 MVI 架构迁移，保留当前 ViewModel 风格，重点放在 Route / Screen / platform bridge 边界清晰

---

## Phase 1（进行中）：Settings 语义源统一

当前设置页已完成渲染层 Compose 化，但搜索索引和配置定义仍绑定 `pref_*.xml`。

- [ ] 用 typed descriptor / registry 替代 `pref_*.xml` 作为搜索和导航元数据源
- [ ] 补齐设置 DSL 中的 warning card 组件
- [ ] 继续允许 Android-only action（目录选择、电池优化等）作为桥接
- [ ] 评估 `SettingsTabbedFragmentsScreen` 中 `AndroidView + FragmentContainerView` 是否可用 Compose tabs 替代

---

## Phase 2（待启动）：Dialog / Sheet 去壳

当前多数 Dialog/Sheet 已有 Compose body 但 host 壳未移除。按优先级排列：

### 高优先

- [ ] `ScrobblingSelectorSheet`：新 Compose 版 `ScrobblingSelectorDialog` 已写好，需要切换路由并删除旧 ViewBinding/RecyclerView 版
- [ ] `ChaptersPagesSheet`：重混合区（BaseAdaptiveSheet + XML TabLayout + Compose HorizontalPager），建议优先拆解
  - [ ] 将 XML TabLayout 替换为 Compose TabRow
  - [ ] 将 BaseAdaptiveSheet 壳替换为 Compose ModalBottomSheet 或内嵌 sheet
  - [ ] 将 `ReadButtonDelegate` + `SplitButton` 迁移为 Compose 组件

### 中优先

- [ ] `DownloadDialogFragment`：Compose body 已完成，需去除 `DialogFragment` 壳（注意保留 Tag 查找兼容）
- [ ] `ContentStatsSheet`：Compose body 已完成，需去除 `BaseAdaptiveSheet` 壳

### 低优先

- [ ] 梳理其余 Dialog/Sheet（欢迎弹层、导入弹窗、筛选弹层等），标注迁移深度

---

## Phase 3（待启动）：Details 去 XML 壳

Details 当前是 L1（XML Activity + ComposeView + BottomSheet Fragment 三层混合）。

- [ ] 将 `DetailsActivity` 从 `ActivityDetailsBinding` 改为 `setContent {}` 直接渲染
- [ ] 将共享元素转场从 XML `imageViewCover` 切换到 Compose 转场方案
- [ ] 将 `ChaptersPagesSheet` 从 Fragment bottom sheet 改为 Compose 内嵌 sheet（与 Phase 2 关联）
- [ ] 评估 `BottomSheetOwner` 接口是否可废弃

---

## Phase 4（待启动）：Liquid Glass 组件补齐

- [ ] 为 `GlassSurface` 接入真实 haze/blur 后端，替换当前 fallback
- [ ] 实现文档规划的 `GlassCard` 组件
- [ ] 实现文档规划的 `GlassSheet` 组件
- [ ] 按设备能力建立降级策略

---

## Phase 5（远期）：共享层抽取

前置条件：上述 Phase 1-4 大部分完成，主流程页面达到 L3。

- [ ] 评估 `shared/designsystem` 模块拆分
- [ ] 评估首批 feature module（Home / Discover）的 commonMain 可行性
- [ ] 评估 Hero 前景卡片、badge、CTA 是否需要进一步组件化
- [ ] 建立 `expect/actual` 平台桥接基础架构
