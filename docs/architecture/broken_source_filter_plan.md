# Kototoro: Broken Source 保留与过滤改进计划

## 1. 背景与当前问题
在当前 Kototoro 与独立解析器插件（如 `kotatsu-parsers` 重新打包的 jar）架构中，由于使用了 `@Broken` 注解来标记失效或出现问题的的漫画源，但该标记在运行时彻底丢失且没有被后续逻辑正确处理，导致以下问题：

1. **编译期丢失 (`Retention` 问题)**
   `@Broken` 注解目前的 `@Retention` 是 `SOURCE`，且为 `internal`。这导致打包出的 `.jar` 字节码中完全丢失了 `@Broken` 注解，宿主 App 即使反射也无法读到它。
2. **状态硬编码问题**
   `KotatsuParserSource` 中的 `isBroken` 变量被硬编码为 `false`，未能正确反映底层 `MangaSource` 或 `ContentSource` 的真实状态。宿主从插件系统加载的源始终被视为正常源。
3. **过滤逻辑缺失**
   在 `ContentSourcesRepository.queryParserSources` 方法中，针对 `excludeBroken = true` 的参数，缺少实际过滤代码（内部只有注释）。
4. **缺少用户控制开关**
   部分高级用户可能仍需要开启或查看这些带有 `Broken` 标记的源（例如临时调试或者部分好用），目前设置里没有类似于“显示已损坏源”的开关，导致这些源要么永远消失，要么永远充斥在列表中。

本计划旨在打通从解析器 `@Broken` 注释定义 -> 插件打包保留 -> 宿主装载解析 -> UI 过滤呈现的整条数据链路，将以上问题全部修复。

## 2. 详细执行方案

为其他大模型或开发人员执行提供以下 6 个修改层的具体指引。

### Layer 1: parser-api 中注解声明的升级
修改 `@Broken` 和各种 `@*SourceParser` 注解，使其保留到运行时，这是基础前提。由于插件中的注解要在宿主 App 被反射读取，需要 `public` 访问修饰符。

**涉及文件：**
- `parser-api/.../org/koitharu/kotatsu/parsers/Broken.kt`
- `parser-api/.../org/Kototoro-app/Kototoro/parsers/Broken.kt`
- `parser-api/.../org/koitharu/kotatsu/parsers/MangaSourceParser.kt`
- `parser-api/.../org/Kototoro-app/Kototoro/parsers/ContentSourceParser.kt`

**修改动作：**
- 注解本身的声明：`@Retention(AnnotationRetention.SOURCE)` 更改为 `@Retention(AnnotationRetention.RUNTIME)`
- 作用域从 `internal annotation class` 更改为 `public annotation class`

---

### Layer 2: 宿主装载时通过 DexFile 检索 Broken 源
当 `JarExtensionLoader` 装载一个 `.jar` 插件时，由于它是 Dalvik `.dex` 字节码，我们需要遍历 DEX 文件找出带有 `@Broken` 注解的类，以提取哪些 Source name 是已损坏的。

**涉及文件：**
- `app/.../org/Kototoro-app/Kototoro/core/extensions/JarExtensionLoader.kt`

**修改动作：**
1. 在 `LoadedJarPlugin` 数据类中，新增一个集合属性 `val brokenSourceNames: Set<String>` 用于存放从该 JAR 解析出来的 broken 解析器名字。
2. 在 `JarExtensionLoader.loadFromDirectory` 方法内（或提取静态方法），利用 `dalvik.system.DexFile` 遍历装载的 DEX 的 `entries()`。
3. 对于每个类，通过 `dexClassLoader.loadClass` 进行加载并使用反射 `clazz.isAnnotationPresent(org.koitharu.kotatsu.parsers.Broken::class.java)`（或对应的 `kototoro` 包下的 `Broken` 注解）进行判断。
4. 若此解析器是 Broken 的，继续反射读取它上面的 `@MangaSourceParser` 或 `@ContentSourceParser` 注解，提取 `name` 属性，并将该 `name` 存入一个 `HashSet`。
5. 在实例化 `LoadedJarPlugin` 回传时传入上述 `HashSet` 作为 `brokenSourceNames`。

*(注：`dalvik.system.DexFile` 虽然在 API 26+ 被标记为 `@Deprecated`，但在当前 Android SDK 中仍完全可用且是读取插件包类元数据最可靠简单的方案。)*

---

### Layer 3: 将 isBroken 标记传播至抽象模型
宿主的 `PluginMangaSource`、`PluginContentSource` 对象需要保留从 JAR 解析出来的 `isBroken` 状态，传递给后续流程。

**涉及文件：**
- `app/.../org/Kototoro-app/Kototoro/core/extensions/GlobalExtensionManager.kt`
- `app/.../org/Kototoro-app/Kototoro/core/parser/kotatsu/KotatsuParserSource.kt`

**修改动作：**
1. **GlobalExtensionManager.kt:**
   - 修改 `PluginMangaSource` 和 `PluginContentSource` 构造方法或接口，增加 `val isBroken: Boolean` 属性。
   - 在 `initialize()` 生成这些 wrapper 时，检查该源的 `name` 是否包含在对应插件的 `plugin.brokenSourceNames` 中，若在则传 `isBroken = true`，否则 `false`。
2. **KotatsuParserSource.kt:**
   - （这是关键包装类）找到类中被硬编码的 `val isBroken: Boolean = false`。
   - 删除硬编码值。改为从宿主内存也就是 `GlobalExtensionManager.mangaSources.value` 找匹配的 `PluginMangaSource` 并返回其 `isBroken` 状态，若找不到则默认置 `false`。

---

### Layer 4: 实现 Repository 层的过滤隔离
真正实现 `queryParserSources` 当 `excludeBroken == true` 时去除损坏插件的操作。

**涉及文件：**
- `app/.../org/Kototoro-app/Kototoro/explore/data/ContentSourcesRepository.kt`

**修改动作：**
找到 `queryParserSources` 方法底部的过滤代码：
```kotlin
if (excludeBroken) {
    // Plugins loaded from the registry are considered valid/not broken
    // If we need to filter broken plugins, we would do it here.
}
```
修改为实际的逻辑：
```kotlin
if (excludeBroken) {
    sources.retainAll { source ->
        val unwrapped = source.unwrap()
        when (unwrapped) {
            is org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource -> !unwrapped.isBroken
            else -> true // 根据未来扩展可以加入其他源类型的 isBroken 判断
        }
    }
}
```

---

### Layer 5: 添加用户控制配置及 XML 开关
引入名为 “显示已损坏/不可用的源 (Show broken sources)” 的配置开关。为了不让失效源污染阅读界面，默认需要关闭 (`false`)。

**涉及文件：**
- `app/.../org/Kototoro-app/Kototoro/core/prefs/AppSettings.kt`
- `app/src/main/res/xml/pref_sources.xml`
- `app/src/main/res/values/strings.xml` 及对应的中文包 `values-zh-rCN/strings.xml`

**修改动作：**
1. 在 `AppSettings` 的常量区新增：`const val KEY_SHOW_BROKEN_SOURCES = "show_broken_sources"`
2. 在 `AppSettings` 中新增属性：
   ```kotlin
   var isShowBrokenSources: Boolean
       get() = prefs.getBoolean(KEY_SHOW_BROKEN_SOURCES, false)
       set(value) = prefs.edit { putBoolean(KEY_SHOW_BROKEN_SOURCES, value) }
   ```
3. 在 `pref_sources.xml` 中的 `<SwitchPreferenceCompat android:key="enable_kotatsu_sources" ...>` 下面，添加一个全新的 `SwitchPreferenceCompat` 取名 `show_broken_sources`。
4. 为其补充合适的 string resource `title` 和 `summary`。

---

### Layer 6: UI (Catalog) 适配查询与传参
当用户在扩展管理、探索区寻找漫画源时，必须根据刚刚配置好的 `AppSettings.isShowBrokenSources` 正确传递拦截指令。

**涉及文件：**
- `app/.../org/Kototoro-app/Kototoro/settings/sources/catalog/SourcesCatalogViewModel.kt`

**修改动作：**
在 `buildSourcesList` 方法调用 `repository.queryParserSources` 的传参中：
将原来的 `excludeBroken = false` 硬编码值修改为 `excludeBroken = !settings.isShowBrokenSources`。

## 3. 测试与验证标准

1. **编译检查**：在做完 `parser-api` 的注解升级后，务必确认整个项目和单独的 `parser` `.jar` 能否正常通过 Gradle 编译和 dex 转换（`d8` 无报错）。
2. **默认场景验证**：在未手动打开“显示损坏的源”设置前，无论宿主还是最新包含有 `@Broken` 源的 jar，在“管理扩展”列表（Catalog）中绝不应出现损坏的解析器项目。
3. **开关验证**：进入“设置 -> 远程源 -> 管理扩展/显示损坏的源”将其打开，返回列表应当立即刷新。此时应该能看到包含 `Broken` 描述和“已关闭/不可用”提示的 source list。

*(本文档供后续跨 Agent 与大模型进行源码修改和重构指导查阅。)*
