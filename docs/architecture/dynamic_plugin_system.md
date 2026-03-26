# Dynamic Plugin System Architecture

## Overview
The Kototoro application features a fully dynamic, decoupled plugin system for parsing content sources. Previously, parsers were directly embedded into the app at compile-time as standard Kotlin extensions (`kototoro-parsers` and `kotatsu-parsers-redo`). This created massive compilation overhead, tightly coupled the app release cycle to parser changes, and bloated the APK size.

The new **Dynamic Plugin System** extracts these parsers into standalone `.jar` (Dex) files that are loaded at runtime. This allows users to hot-swap extensions, develop custom forks, and import external parser collections without needing an app update or modifying the core Kototoro codebase.

## Core Concepts

### 1. `parser-api` Module
A shared, lightweight Android library module that defines all the foundational interfaces (`ContentParser`, `MangaParser`, `ContentSource`, `ContentLoaderContext`, etc.). 
- Both the **host app** (Kototoro) and the **parser plugins** depend on `parser-api` as heavily version-controlled contracts.
- Plugins depend on this as `compileOnly` so they do not bundle the interfaces themselves, minimizing duplicate classes and guaranteeing runtime compatibility.

### 2. Zero-Overhead ClassLoading (`PluginClassLoader`)
When evaluating plugin solutions, Android’s standard `DexClassLoader` isolates the loaded classes but requires cumbersome `java.lang.reflect.Proxy` wrappers to map Host interfaces to Plugin interfaces, causing massive reflection overhead on every method call.

To achieve **zero runtime performance overhead**, Kototoro introduces a custom parent-delegation strategy:
* The `PluginClassLoader` forcefully intercepts requests for classes in the `parser-api` namespaces (`org.koitharu.kotatsu.parsers.*` api classes) and delegates them *directly* to the Host App’s ClassLoader.
* By sharing the exact same `Class<?>` reference in memory for the interface definitions, the Host App can simply cast the instantiated plugin objects (e.g., `val parser = pluginInstance as ContentParser`), negating the need for any reflection during runtime execution.

### 3. Namespace Isolation & Dual Architecture
Each imported `.jar` file is loaded into its own distinct `PluginClassLoader` instance. This completely avoids class conflicts for identical paths (e.g., two different plugins can both have `org.koitharu.kotatsu.parsers.model.MangaParserSource` enum or a specific site parser). 
* **Dual Architecture:** Kototoro supports loading both legacy Kotatsu parser collections (`MangaParserFactoryKt`) and native Kototoro content collections (`ContentParserFactoryKt`) interchangeably.

### 4. `GlobalExtensionManager` (Single Source of Truth)
The app state, UI, and Database (Data Layer) now interact solely with `GlobalExtensionManager`.
* **Aggregation:** It aggregates all discovered `ContentSource`s from `JarExtensionLoader` (JAR plugins) and `ApkExtensionLoader` (Mihon/Aniyomi/IReader APKs).
* **Deduplication:** Sources are namespace-prefixed internally.
* **Reactive Layout:** The manager uses Kotlin Coroutines and `MutableStateFlow` to broadcast state changes across the application whenever a user imports or deletes a plugin via the settings menu, automatically refreshing the exploration views.

## User Workflow
1. Users navigate to Settings > Remote Sources > **Installed Plugins**.
2. They tap **Import Plugin (.jar)** to select a compatible `plugin.jar` (which must be a Dalvik/Dex compatible bytecode archive).
3. The app copies the JAR to its private `files/plugins/` directory and triggers `GlobalExtensionManager.initialize(context)`.
4. The plugins are immediately available, and the UI reacts seamlessly.

## Plugin Build Pipeline
A standard JAR cannot be loaded directly on Android. The generation of these plugins follows a specific pipeline orchestrated via GitHub Actions:
1. The parser code remains in separate repositories (e.g., `skepsun/kototoro-parsers`).
2. Standard Kotlin compilation produces a standard `.jar`.
3. The Android SDK's **`d8` (Dexer)** tool converts the `.class` files into Dalvik `classes.dex` and packages it into an Android-compatible `plugin.jar`.
4. **Manifest Generation**: The GitHub Actions runner executes `generate_index.py` during release, automatically creating an `index.min.json` mapping all hosted `.jar` versions.
5. These artifacts (`index.min.json` and the corresponding versions) are distributed to a specific `repo` branch to mirror standard extension repos (like Mihon).

### 5. `JAR` ABI Compatibility Constraints
Because the plugin APK operates on the `ClassLoader` boundary by deferring to the Host App's `parser-api`, the ABI (method signatures and return types) MUST match exactly. 
If the Host changes an interface (e.g., converting `val source: ContentParserSource` to `val source: ContentSource`), the plugin's `ContentParserSource` enum classloader boundary must be forcefully contained by modifying `JarExtensionLoader` to allow the plugin to class-load its own bytecode instead of deferring to a non-existent Host implementation.
