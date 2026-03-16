# TVBox 原生支持改进计划 — 去除 Companion 进程

## 背景

当前 Kototoro 通过 Companion 进程（`com.github.tvbox.osc`）运行 TVBox jar spider，使用 IPC 通信。分析了 TV（FongMi）、TVBoxOS、XMBOX 三个空壳客户端后发现：

- **所有空壳客户端都在主进程直接加载和执行 spider**
- **没有任何客户端有 [DexNative](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt#2079-2089)** — 它由 Guard spider jar 自身注入
- **所有客户端共享完全一致的加载合约**

Kototoro 的 `TVBoxJarSpiderExecutor.createSpider()` 已经实现了与空壳客户端相同的加载流程，但当前被包装在 Companion 进程中通过 IPC 调用。本计划目标是将 jar spider 执行迁移到主进程，完全去除 Companion 进程。

---

## 架构对比

### 当前架构（Companion IPC）
```
Kototoro Main Process
  └── TVBoxJarSpiderIsolatedRuntime
        └── TVBoxJarSpiderRemoteClient (AIDL/IPC)
              └── TVBox Companion Process (com.github.tvbox.osc)
                    ├── TVBoxJarSpiderService
                    ├── TVBoxJarSpiderExecutor (jar loading + spider)
                    └── com.github.tvbox.osc.base.App (bridge Application)
```

### 目标架构（主进程直接执行）
```
Kototoro Main Process
  └── TVBoxJarSpiderRuntime (已有，直接使用)
        ├── DexClassLoader (jar loading)
        ├── com.github.tvbox.osc.base.App (bridge, 已有 App.java)
        ├── com.github.catvod.net.OkHttp (需嵌入)
        ├── com.github.catvod.utils.Path (需嵌入)
        └── Spider 实例 (直接调用)
```

---

## 需要做的改动

### Phase 1: 补齐 catvod 运行时依赖

Spider 在运行时会通过 `Init.context()` 和 `Spider.client()` 调用宿主提供的类。当前 `tvbox-companion` 模块中有 [App.java](file:///Users/sunchuxiong/kotatsu_demo/TV/app/src/main/java/com/fongmi/android/tv/App.java) bridge，但缺少 spider 实际依赖的 [catvod](file:///Users/sunchuxiong/kotatsu_demo/TV/catvod) 运行时工具类。

#### [NEW] `com.github.catvod.net.OkHttp`
- 从 FongMi 的 [catvod](file:///Users/sunchuxiong/kotatsu_demo/TV/catvod) 模块移植 [OkHttp.java](file:///Users/sunchuxiong/kotatsu_demo/TV/catvod/src/main/java/com/github/catvod/net/OkHttp.java)
- 提供 `OkHttp.client()`、`OkHttp.string(url)`、`OkHttp.newCall()` 等
- Spider 通过 `Spider.client()` 调用此类
- 可包装 Kototoro 已有的 `LegadoHttpClient` / OkHttp 实例

#### [NEW] `com.github.catvod.utils.Path`
- 移植 [Path.java](file:///Users/sunchuxiong/kotatsu_demo/TV/catvod/src/main/java/com/github/catvod/utils/Path.java)，提供 `Path.cache()`、`Path.files()`、`Path.jar()` 等
- 内部依赖 `Init.context().getCacheDir()`（已由 App bridge 提供）

#### [NEW] `com.github.catvod.utils.Util`
- 移植 [Util.java](file:///Users/sunchuxiong/kotatsu_demo/TV/catvod/src/main/java/com/github/catvod/utils/Util.java)，提供 `Util.md5()`、`Util.equals()` 等工具方法

> [!NOTE]
> 这些类放入 `tvbox-companion` 模块（重命名为 `tvbox-runtime`）或直接放入 [app](file:///Users/sunchuxiong/kotatsu_demo/TV/app) 模块均可。

---

### Phase 2: 切换 Runtime 路由

#### [MODIFY] [TVBoxSpiderRuntimeFactory.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxSpiderRuntimeFactory.kt)

将 type==3 的路由从 [TVBoxJarSpiderIsolatedRuntime](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderIsolatedRuntime.kt#40-874) 切换到 [TVBoxJarSpiderRuntime](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRuntime.kt#41-1014)：

```diff
-config.site.type == 3 || ... -> TVBoxJarSpiderIsolatedRuntime(...)
+config.site.type == 3 || ... -> TVBoxJarSpiderRuntime(...)
```

#### [MODIFY] [TVBoxJarSpiderRuntime.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRuntime.kt)

- 合并 [TVBoxJarSpiderExecutor](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt#44-2143) 中最新的 Guard 支持逻辑（DexNative init、初始化顺序、JNI 空安全等）
- 确保 `App.init(context)` 和 `App.configureRuntimeClassLoader()` 在 spider 加载前被正确调用
- 此类已有完整的 spider 生命周期管理（~1100 LOC），只需同步最新修复

---

### Phase 3: 清理 Companion 代码

#### [DELETE] 以下文件不再需要：

| 文件 | 用途 |
|------|------|
| [TVBoxJarSpiderIsolatedRuntime.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderIsolatedRuntime.kt) | IPC 调用层 (962 LOC) |
| [TVBoxJarSpiderRemoteClient.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRemoteClient.kt) | AIDL/IPC 客户端 |
| [TVBoxJarSpiderRemoteModels.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderRemoteModels.kt) | IPC 数据模型 |
| [TVBoxJarSpiderService.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderService.kt) | Companion Service |
| [TVBoxJarSpiderServiceEntryPoint.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderServiceEntryPoint.kt) | Service entry point |
| [TVBoxCompanionAvailability.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxCompanionAvailability.kt) | Companion 可用性检测 |
| [TVBoxJarSpiderExecutor.kt](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt) | Companion 端 executor（逻辑合并到 Runtime 后删除）|

#### [DELETE] 模块级清理：

| 目标 | 说明 |
|------|------|
| `tvbox-companion` 模块 | 整个模块可移除或重命名为 `tvbox-runtime` |
| `tvbox-bridge-api` 模块 | IPC 协议模型可删除（`TVBoxJarSpiderRequest`/[Response](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt#1819-1825)/`WorkerProtocol` 等）|
| [settings.gradle](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/settings.gradle) | 移除 `include ':tvbox-companion'`、`include ':tvbox-bridge-api'` |
| [AndroidManifest.xml](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/AndroidManifest.xml) | 移除 Companion Service、BootstrapActivity、BootstrapProvider 声明 |

---

### Phase 4: 整合 App bridge

#### [MODIFY] 保留的 `com.github.tvbox.osc.base.App.java`

当前已有的 [App.java](file:///Users/sunchuxiong/kotatsu_demo/TV/app/src/main/java/com/fongmi/android/tv/App.java) bridge（在 `tvbox-companion` 和 [app](file:///Users/sunchuxiong/kotatsu_demo/TV/app) 模块各有一份）需要：
- 合并为唯一一份，移入 [app](file:///Users/sunchuxiong/kotatsu_demo/TV/app) 模块
- 保持 [getPackageName() = "com.github.tvbox.osc"](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/tvbox-companion/src/main/java/com/github/tvbox/osc/base/App.java#324-328) 伪装
- 保持 [BridgedPackageManager](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/tvbox-companion/src/main/java/com/github/tvbox/osc/base/App.java#399-937) 包名映射
- [getClassLoader()](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxJarSpiderExecutor.kt#2083-2085) 返回宿主 app classloader（不再需要 `bridgedClassLoader`）
- [getCacheDir()](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/app/src/main/java/com/github/tvbox/osc/base/App.java#266-270) / [getFilesDir()](file:///Users/sunchuxiong/kotatsu_demo/Kototoro/tvbox-companion/src/main/java/com/github/tvbox/osc/base/App.java#258-266) 直接返回宿主目录（或带 tvbox 子目录隔离）

---

## 验证计划

### 自动化验证
```bash
# 编译检查
./gradlew assembleDebug

# 确认 companion 模块已移除
# settings.gradle 不包含 tvbox-companion / tvbox-bridge-api
```

### 功能测试
1. **普通 csp spider**（非 Guard）：`adb logcat -s TVBoxJarRuntime`，确认正常加载和数据返回
2. **Guard spider**（如 WexReBoGuard）：确认 delegate 解析、JNI 加载、数据返回
3. **QuickJS spider**（type=4）：确认不受影响
4. **Proxy 代理**：确认视频播放代理正常工作

### 关键日志标记
```
TVBoxJarRuntime: Reusing cached TVBox jar loader
TVBoxJarRuntime: Constructed TVBox spider instance
TVBoxJarRuntime: Initialized TVBox spider instance
```

---

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Guard JNI 崩溃 (SIGABRT) | 主进程闪退 | 接受（与 TVBoxOS/FongMi 一致），用户可通过不加载 Guard 源规避 |
| Spider 内存泄漏 | 主进程内存增长 | DexClassLoader 缓存机制已有，与当前一致 |
| Spider 线程阻塞 | 影响主进程 UI | 已有 `spiderExecutor` 单线程池 + 超时机制 |
| 缺少 catvod 运行时类 | Spider 调用 `OkHttp.client()` 时 ClassNotFound | Phase 1 补齐 |
