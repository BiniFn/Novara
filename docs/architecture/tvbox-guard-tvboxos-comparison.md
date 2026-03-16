# TVBox Guard 与 TVBoxOS 对照结论

状态日期：2026-03-14

本文只回答一个问题：

- 为什么 Kototoro 的 `Guard` 类型 TVBox jar 总是不兼容
- 对照 `TVBoxOS` 之后，这个问题应当如何定性

## 一、先看 TVBoxOS 的最小加载路径

`TVBoxOS` 的 `JarLoader` 主路径非常直接：

1. 下载或复用 jar 到 `filesDir/csp/<md5>.jar`
2. 用 `DexClassLoader(jar, cacheDir/catvod_csp, null, App.getInstance().getClassLoader())` 创建类加载器
3. 反射调用 `com.github.catvod.spider.Init.init(App.getInstance())`
4. 反射创建 `com.github.catvod.spider.<cls>` 实例
5. 调用 `spider.init(App.getInstance(), ext)`

关键点只有三个：

- 宿主是 `App.getInstance()`
- `Init.init(context)` 在实例化前执行
- jar 通过普通 `DexClassLoader` 装入

对应文件：

- [`JarLoader.java`](/E:/kototoro_demo/TVBoxOS/app/src/main/java/com/github/catvod/crawler/JarLoader.java)
- [`App.java`](/E:/kototoro_demo/TVBoxOS/app/src/main/java/com/github/tvbox/osc/base/App.java)

## 二、Kototoro 当前其实已经基本对齐了这条 Java 路径

Kototoro 当前的 `type = 3 / csp_*` 运行时并不是“完全没按 TVBoxOS 做”。

已对齐的点：

- worker 进程启动时会先 `App.init(applicationContext)`
- worker 进程会初始化 `OkGoHelper`
- jar 仍通过 `DexClassLoader` 装入
- 实例化 spider 前会先尝试 `Init.init(context)`
- 实例化后仍会调用 `spider.init(context, ext)`
- 对 `Guard` 源已改成普通 `DexClassLoader`，不再走 `ChildFirstDexClassLoader`

对应文件：

- [`TVBoxJarSpiderService.kt`](/E:/kototoro_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderService.kt)
- [`TVBoxJarSpiderExecutor.kt`](/E:/kototoro_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt)
- [`App.java`](/E:/kototoro_demo/Kototoro/app/src/main/java/com/github/tvbox/osc/base/App.java)

这意味着：

- 问题已经不能简单归类为“没参考 TVBoxOS”
- 至少在 Java 层主流程上，Kototoro 已经接近 `TVBoxOS JarLoader` 的语义

## 三、Guard jar 的证据和普通 spider 完全不是一个量级

本地样例 [`tmp_tvbox_guard.jar`](/E:/kototoro_demo/Kototoro/tmp_tvbox_guard.jar) 的包结构只有：

- `classes.dex`
- `assets/wexguard_v7.so`
- `assets/wexguard_v8.so`
- `assets/wexshinidie.guard`

这个结构说明它不是“普通反射 spider jar”，而是：

- `dex`
- 本地 `so`
- guard 数据文件

三件套一起工作的 native 型运行时。

因此它的兼容问题天然比普通 `csp_*` jar 更重。

## 四、为什么之前的结论是对的

Kototoro 现有文档里已经记录过一个阶段性事实：

- 早期失败点是 `DexNative.<clinit>` 里 `Context.getCacheDir()` 空指针
- 这个问题已经跨过去了
- 后续失败进入 `CRASH_NATIVE + JNI DETECTED ERROR IN APPLICATION`

对应文档：

- [`tvbox-integration-implementation-plan.md`](/E:/kototoro_demo/Kototoro/docs/architecture/tvbox-integration-implementation-plan.md)
- [`tvbox-guard-bridge-service-design.md`](/E:/kototoro_demo/Kototoro/docs/architecture/tvbox-guard-bridge-service-design.md)

这很关键，因为它说明：

- 当前主阻塞点不再是 `Init.init()` 是否调用
- 不再是 `App.getInstance()` 是否有 `cacheDir`
- 也不再是普通 `DexClassLoader` 装载顺序问题

真正的问题已经进入：

- `Guard so/JNI` 对宿主环境的假设
- 本地 native 装载与运行语义
- 当前应用环境与 TVBoxOS 原始宿主之间的深层差异

## 五、对照后的定性

对照 `TVBoxOS` 后，可以把结论收敛为一句话：

`Guard` 不兼容的根因，更像是 native/JNI 宿主不兼容，而不是普通 jar 兼容层遗漏。

更具体地说：

- `TVBoxOS` 的普通 `JarLoader` 语义，Kototoro 已基本复刻
- `Guard` jar 自身又明确携带本地 `so`
- 当前错误已经从 Java 侧空上下文推进到 JNI 崩溃

所以继续在 Kotlin/Java 层无限追加小补丁，收益会越来越低。

## 六、建议的工程方向

### 1. 不再把 Guard 问题误判为“普通 jar 没兼容完”

后续日志、文档、任务拆分都应把 `Guard` 单独列出，不再和普通 `csp_*` jar 混为一类。

### 2. 本地 runtime 继续服务普通 TVBox spider

当前本地隔离 worker 路线对普通 spider 仍然有价值，不应因为 `Guard` 问题整体否定。

### 3. Guard 优先走桥接服务

如果目标是尽快恢复可用性，`Guard` 最现实的方向仍然是桥接服务：

- 客户端不直接执行 `Guard so`
- 远端执行环境承担 `jar/guard/native` 复杂性
- 客户端只消费标准化结果

### 4. 文档和产品提示都要明确区分

建议支持矩阵明确写成：

- A: 直链 / M3U / 简单 CMS
- B: QuickJS TVBox
- C: 普通 jar spider
- D: Guard-native jar，需要桥接或专用宿主

## 七、结论

参考 `TVBoxOS` 之后，当前最重要的结论不是“Kototoro 没按 TVBoxOS 做”，而是：

- Kototoro 已经基本对齐了 `TVBoxOS JarLoader` 的 Java 层主流程
- `Guard` jar 的问题主要出在 native/JNI 宿主层
- 这类问题继续本地硬对齐的投入产出比很差
- 下一步应优先走 `Guard Bridge Runtime` 或其他远程执行方案
