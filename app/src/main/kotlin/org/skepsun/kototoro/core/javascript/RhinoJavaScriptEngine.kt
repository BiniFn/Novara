package org.skepsun.kototoro.core.javascript

import android.content.Context
import android.util.Log
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import java.net.CookieManager

/**
 * 基于 Rhino 的 JavaScript 引擎实现
 * 
 * 使用 Mozilla Rhino 引擎执行 JavaScript 代码，提供 Legado 兼容的 API
 */
class RhinoJavaScriptEngine(
    private val httpClient: LegadoHttpClient,
    private val cookieManager: CookieManager,
    private val cookieJar: PersistentCookieJar,
    private val androidContext: Context
) : JavaScriptEngine {
    
    private var rhinoContext: RhinoContext? = null
    private var scope: Scriptable? = null
    private var javaAPI: LegadoJavaAPI? = null
    private var cookieAPI: LegadoCookieAPI? = null
    
    init {
        Log.i(TAG, "Initializing RhinoJavaScriptEngine...")
        initializeEngine()
        Log.i(TAG, "RhinoJavaScriptEngine initialization completed")
    }
    
    /**
     * 初始化 Rhino 引擎
     */
    private fun initializeEngine() {
        Log.i(TAG, "Starting Rhino engine initialization...")
        try {
            rhinoContext = RhinoContext.enter()
            rhinoContext?.let { ctx ->
                // 设置优化级别为 -1（解释模式，Android 兼容）
                ctx.optimizationLevel = -1
                
                // 设置语言版本为 ES6 (200)
                ctx.languageVersion = 200
                
                // 初始化标准对象
                scope = ctx.initStandardObjects()
                
                // 创建 Legado API 实例
                val javaAPI = LegadoJavaAPI(httpClient, cookieManager, androidContext, cookieJar)
                val cookieAPI = LegadoCookieAPI(cookieJar)
                
                // 注册 Legado API
                registerGlobalObject("java", javaAPI)
                registerGlobalObject("cookie", cookieAPI)
                
                // 保存 API 引用以便后续设置上下文
                this.javaAPI = javaAPI
                this.cookieAPI = cookieAPI
                
                // 注册全局辅助函数
                ctx.evaluateString(scope, "function bhost() { return java.bhost(); }", "init", 1, null)
                // legado-with-MD3 兼容：部分规则使用 Reload(url) 动态加载远端 JS（通常配合 eval(String(Reload(url)))）。
                // 这里将其映射到 java.ajax，保持通用性（不做站点特判）。
                ctx.evaluateString(
                    scope,
                    "function Reload(url, options) {" +
                        "  if (typeof options === 'undefined' || options === null) {" +
                        "    return java.ajax(String(url));" +
                        "  }" +
                        "  return java.ajax(String(url), options);" +
                        "}",
                    "init",
                    1,
                    null
                )
                // legado-with-MD3 兼容：部分三方脚本依赖通用 helper（Get/Put/Map/get/explore）。
                // 仅在未定义时注入，避免覆盖源脚本自身实现。
                ctx.evaluateString(
                    scope,
                    "" +
                        // Put(x): legado 常用来“提交结果/刷新 result 变量”，这里对齐为：写入全局 result 并返回写入值。
                        "if (typeof Put === 'undefined') {" +
                        "  function Put(x) {" +
                        "    var v = x;" +
                        "    try {" +
                        "      if (v && typeof v === 'object') {" +
                        "        v = JSON.stringify(v);" +
                        "      }" +
                        "    } catch (e) {}" +
                        "    try { result = v; } catch (e) {}" +
                        "    return v;" +
                        "  }" +
                        "}" +
                        // 避免与 ES6 Map 构造器冲突：三方 legado 脚本常把 Map 当作“输入框/配置项读取”的函数使用（Map('xxx')）。
                        // Rhino 中内置 Map 通常要求 `new Map()`，会导致脚本报错；这里对齐 legado 环境，强制提供 Map() 函数。
                        "try { if (typeof NativeMap === 'undefined' && typeof Map !== 'undefined') { var NativeMap = Map; } } catch (e) {}" +
                        "function Map(label) {" +
                        "  try {" +
                        "    if (typeof source !== 'undefined' && source && typeof source.get === 'function') {" +
                        "      var v = String(source.get(String(label)) || '');" +
                        "      if (v.length > 0) return v;" +
                        "    }" +
                        "  } catch (e) {}" +
                        "  return '';" +
                        "}" +
                        "if (typeof Get === 'undefined') {" +
                        "  function Get(key, defVal) {" +
                        "    try { if (typeof $$$ !== 'undefined' && $$$ && typeof $$$[key] !== 'undefined') return $$$[key]; } catch (e) {}" +
                        "    try {" +
                        "      if (typeof source !== 'undefined' && source && typeof source.getVariable === 'function') {" +
                        "        var raw = String(source.getVariable() || '');" +
                        "        if (raw && raw.trim().length > 0) {" +
                        "          var obj = JSON.parse(raw);" +
                        "          if (obj && typeof obj[key] !== 'undefined') return obj[key];" +
                        "        }" +
                        "      }" +
                        "    } catch (e) {}" +
                        "    if (typeof defVal !== 'undefined') return defVal;" +
                        "    return '';" +
                        "  }" +
                        "}" +
                        "if (typeof explore === 'undefined') {" +
                        "  function explore(title, url, style, weight, open) {" +
                        "    return { title: String(title || ''), url: (url == null ? '' : String(url)), style: style, weight: weight, open: open };" +
                        "  }" +
                        "}" +
                        "if (typeof get === 'undefined') {" +
                        "  function get(name, value, page) {" +
                        "    var v = value;" +
                        "    if (name === 'ordering') { return (v && v !== 0 && v !== '0') ? ('?ordering=' + encodeURIComponent(v)) : '?'; }" +
                        "    if (name === 'top') { return (v && v !== 0 && v !== '0') ? ('&top=' + encodeURIComponent(v)) : ''; }" +
                        "    if (name === 'audience') { return (v && v !== 0 && v !== '0') ? ('&audience=' + encodeURIComponent(v)) : ''; }" +
                        "    if (name === 'orderby') {" +
                        "      var i = parseInt(v, 10);" +
                        "      if (isNaN(i)) i = 0;" +
                        "      var p = parseInt(page, 10);" +
                        "      if (isNaN(p) || p < 1) p = 1;" +
                        "      var types = ['total','month','week','day'];" +
                        "      var t = types[i] || 'total';" +
                        "      var offset = 24 * (p - 1);" +
                        "      return 'ranks?date_type=' + t + '&_update=true&limit=24&offset=' + offset;" +
                        "    }" +
                        "    return '';" +
                        "  }" +
                        "}",
                    "init",
                    1,
                    null
                )
                
                Log.i(TAG, "Rhino engine initialized successfully with Legado APIs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Rhino engine", e)
        }
    }
    
    override fun execute(script: String, context: JavaScriptContext): Any? {
        val ctx = rhinoContext ?: run {
            Log.e(TAG, "Rhino context not initialized")
            return null
        }
        
        val currentScope = scope ?: run {
            Log.e(TAG, "Rhino scope not initialized")
            return null
        }
        
        return try {
            // 确保在正确的Rhino上下文中执行
            val currentCtx = RhinoContext.enter()
            try {
                // Configure the current thread's context
                currentCtx.optimizationLevel = -1
                currentCtx.languageVersion = 200
                
                // 设置上下文变量
                setContextVariables(currentScope, context)
                
                // 设置 JavaScript 上下文到 API 中
                javaAPI?.jsContext = context
                
                // 重要：每次执行前重新注册 java 对象
                // Rhino 的标准对象中包含 "java" 作为 JavaPackage，会覆盖我们的绑定
                // 所以需要在每次执行前显式设置
                javaAPI?.let { api ->
                    val wrappedApi = RhinoContext.javaToJS(api, currentScope)
                    ScriptableObject.putProperty(currentScope, "java", wrappedApi)
                    Log.d(TAG, "Re-registered 'java' API object before script execution")
                }
                
                // 设置 HTML 内容到 LegadoJavaAPI
                // 优先使用 context.result，然后尝试 getVariable("result")
                val contentToSet = context.result ?: context.getVariable("result")
                Log.d(TAG, "Content for LegadoJavaAPI: context.result=${context.result?.javaClass?.simpleName}, getVariable=${context.getVariable("result")?.javaClass?.simpleName}")
                if (contentToSet != null) {
                    javaAPI?.setContent(contentToSet)
                    Log.d(TAG, "Set HTML content to LegadoJavaAPI: type=${contentToSet.javaClass.simpleName}, preview=${contentToSet.toString().take(100)}...")
                } else {
                    Log.w(TAG, "No content to set for LegadoJavaAPI - java.getElements() will fail!")
                }
                
                // 执行脚本
                val evalResult = currentCtx.evaluateString(currentScope, wrapScript(script), "script", 1, null)
                
                // 返回脚本的评估结果
                // 如果评估结果是 undefined，则检查其他可能的返回值
                val jsResult = RhinoContext.jsToJava(evalResult, Any::class.java)
                if (jsResult == null || jsResult.toString() == "undefined") {
                    // 1. 检查 result 变量是否被修改
                    val resultVar = ScriptableObject.getProperty(currentScope, "result")
                    if (resultVar != Scriptable.NOT_FOUND) {
                        val resultValue = RhinoContext.jsToJava(resultVar, Any::class.java)
                        if (resultValue != null && resultValue.toString() != "undefined") {
                            return resultValue
                        }
                    }
                    
                    // 2. 检查 path 变量（Legado 列表规则常用）
                    val pathVar = ScriptableObject.getProperty(currentScope, "path")
                    if (pathVar != Scriptable.NOT_FOUND) {
                        val pathValue = RhinoContext.jsToJava(pathVar, Any::class.java)
                        if (pathValue != null && pathValue.toString() != "undefined") {
                            Log.d(TAG, "Using path variable as return value: $pathValue")
                            return pathValue
                        }
                    }
                    
                    // 3. 对于起点等网站的列表规则，检查是否执行了 getElement 操作
                    // 如果脚本中调用了 java.getElement(path)，我们应该返回 path 作为选择器
                    if (script.contains("java.getElement(path)") || script.contains("java.getElement('class.res-book-item')")) {
                        // 检查 path 变量
                        val pathVar = ScriptableObject.getProperty(currentScope, "path")
                        if (pathVar != Scriptable.NOT_FOUND) {
                            val pathValue = RhinoContext.jsToJava(pathVar, Any::class.java)
                            if (pathValue != null && pathValue.toString() != "undefined") {
                                Log.d(TAG, "Returning path for getElement operation: $pathValue")
                                return pathValue
                            }
                        }
                        
                        // 如果没有 path 变量，但脚本包含 class.res-book-item，直接返回这个选择器
                        if (script.contains("class.res-book-item")) {
                            Log.d(TAG, "Returning default selector for Qidian: class.res-book-item")
                            return "class.res-book-item"
                        }
                    }
                    
                    // 4. 检查是否有 LegadoJavaAPI 执行了 getElement 操作
                    // 如果 JavaScript 调用了 java.getElement()，API 可能已经处理了DOM操作
                    // 我们需要从 JavaScript 上下文中获取最后设置的 path 值
                    javaAPI?.let { api ->
                        // 尝试从 JavaScript 变量中获取 path
                        val pathFromJS = api.get("path")
                        if (pathFromJS != null && pathFromJS.toString().isNotEmpty()) {
                            Log.d(TAG, "Returning path from JavaScript variables: $pathFromJS")
                            return pathFromJS
                        }
                        
                        // 尝试获取最后使用的选择器
                        val lastSelector = api.get("lastSelector")
                        if (lastSelector != null && lastSelector.toString().isNotEmpty()) {
                            Log.d(TAG, "Returning lastSelector from JavaScript API: $lastSelector")
                            return lastSelector
                        }
                    }
                    
                    // 5. 对于起点的特殊情况，如果脚本执行了完整的验证流程
                    // 但没有返回值，我们假设它成功完成了验证，返回默认选择器
                    if (script.contains("startBrowserAwait") && script.contains("class.res-book-item")) {
                        Log.d(TAG, "Detected Qidian browser verification script, returning default selector")
                        return "class.res-book-item"
                    }
                    
                    // 4. 如果都没有，返回原始结果
                    jsResult
                } else {
                    jsResult
                }
            } finally {
                RhinoContext.exit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Script execution failed: ${scriptPreviewForLog(script)}")
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun scriptPreviewForLog(script: String, limit: Int = 220): String {
        val normalized = script.replace("\r", "").replace("\n", "\\n").trim()
        if (normalized.length <= limit) return normalized
        return normalized.take(limit) + "…(len=${normalized.length})"
    }
    
    override fun evaluate(expression: String, context: JavaScriptContext): Any? {
        return execute(expression, context)
    }
    
    override fun registerGlobalObject(name: String, obj: Any) {
        val currentScope = scope ?: run {
            Log.e(TAG, "Cannot register global object: scope not initialized")
            return
        }
        
        try {
            val wrappedObj = RhinoContext.javaToJS(obj, currentScope)
            ScriptableObject.putProperty(currentScope, name, wrappedObj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register global object: $name", e)
        }
    }
    
    override fun dispose() {
        try {
            RhinoContext.exit()
            rhinoContext = null
            scope = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispose Rhino engine", e)
        }
    }
    
    /**
     * 设置上下文变量到 JavaScript 作用域
     */
    private fun setContextVariables(currentScope: Scriptable, context: JavaScriptContext) {
        val ctx = rhinoContext ?: return
        
        // 获取所有变量（包括标准变量和自定义变量）
        val allVariables = context.getAllVariables()

        fun toJsValue(value: Any?): Any? {
            return when (value) {
                null -> null
                is List<*> -> {
                    Log.d(TAG, "Converting List to NativeArray, size=${value.size}")
                    val array = ctx.newArray(currentScope, value.size)
                    value.forEachIndexed { index, item ->
                        ScriptableObject.putProperty(array, index, toJsValue(item))
                    }
                    array
                }
                is Array<*> -> {
                    Log.d(TAG, "Converting Array to NativeArray, size=${value.size}")
                    val array = ctx.newArray(currentScope, value.size)
                    value.forEachIndexed { index, item ->
                        ScriptableObject.putProperty(array, index, toJsValue(item))
                    }
                    array
                }
                is Map<*, *> -> {
                    // Legado 规则大量依赖 `result.xxx` 访问 JSON 字段；
                    // Rhino 对 Java Map 的点语法兼容不稳定，这里显式转换为 JS Object。
                    val obj = ctx.newObject(currentScope)
                    value.forEach { (k, v) ->
                        val key = k?.toString() ?: return@forEach
                        ScriptableObject.putProperty(obj, key, toJsValue(v))
                    }
                    obj
                }
                is String -> {
                    // 确保 Java String 在 Rhino 中可以访问 String.prototype 方法（如 match）
                    ctx.newObject(currentScope, "String", arrayOf(value))
                }
                else -> RhinoContext.javaToJS(value, currentScope)
            }
        }

        // 设置所有变量到 JavaScript 作用域
        allVariables.forEach { (name, value) ->
            // 特殊处理 result 变量：如果是单元素 List，转换为 String
            // 这是因为 JS 链式规则通常期望 result 是一个字符串
            val effectiveValue = if (name == "result" && value is List<*> && value.size == 1) {
                Log.d(TAG, "Converting single-item List to String for 'result': ${value[0]}")
                value[0]?.toString() ?: ""
            } else {
                value
            }

            val jsValue = toJsValue(effectiveValue)
            ScriptableObject.putProperty(currentScope, name, jsValue)
        }
        
        // 为 source 对象添加 getKey() 方法
        context.source?.let { source ->
            val sourceWrapper = SourceWrapper(source, androidContext)
            val jsSource = RhinoContext.javaToJS(sourceWrapper, currentScope)
            ScriptableObject.putProperty(currentScope, "source", jsSource)
        }
        
        // 为 book 对象添加 putVariable/getVariable/setUseReplaceRule 方法
        context.book?.let { book ->
            val sourceKey = context.source?.bookSourceUrl.orEmpty()
            val bookWrapper = BookWrapper(book, androidContext, sourceKey)
            val jsBook = RhinoContext.javaToJS(bookWrapper, currentScope)
            ScriptableObject.putProperty(currentScope, "book", jsBook)
        }
    }

    
    /**
     * Source 对象包装器，提供 Legado 兼容的方法
     */
    class SourceWrapper(
        private val source: org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource,
        private val context: Context
    ) {
        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        // 属性访问器 - Rhino 会自动调用这些 getter 方法
        val bookSourceComment: String? get() = source.bookSourceComment
        val bookSourceName: String get() = source.bookSourceName
        val bookSourceUrl: String get() = source.bookSourceUrl
        val bookSourceType: Int get() = source.bookSourceType
        val bookSourceGroup: String? get() = source.bookSourceGroup
        val header: String? get() = source.header
        val loginUrl: String? get() = source.loginUrl
        val loginUi: String? get() = source.loginUi
        val loginCheckJs: String? get() = source.loginCheckJs
        val enabled: Boolean get() = source.enabled
        val weight: Int get() = source.weight

        fun getKey(): String {
            return source.bookSourceUrl
        }

        /**
         * legado-with-MD3 兼容：源变量读写（JS 中常用 source.getVariable()/setVariable()）
         *
         * 该变量用于跨请求/跨规则保存少量状态（例如登录 token、设备注册信息等）。
         */
        fun getVariable(): String {
            return prefs.getString(sourceVariableKey(), "") ?: ""
        }

        fun setVariable(variable: String?) {
            prefs.edit().apply {
                if (variable == null) remove(sourceVariableKey()) else putString(sourceVariableKey(), variable)
            }.apply()
        }

        /**
         * legado-with-MD3 兼容：持久化 KV（JS 中常用 source.put()/source.get()）
         */
        fun put(key: String, value: String): String {
            prefs.edit().putString(kvKey(key), value).apply()
            return value
        }

        fun get(key: String): String {
            return prefs.getString(kvKey(key), "") ?: ""
        }

        /**
         * legado-with-MD3 兼容：登录信息读写（部分源脚本用于保存可变基址、token 等）
         * Kototoro 这里按明文存储（不做 legado 的 AES 加密），避免引入额外依赖与复杂度。
         */
        fun putLoginInfo(info: String): Boolean {
            return runCatching {
                prefs.edit().putString(loginInfoKey(), info).apply()
            }.isSuccess
        }

        fun getLoginInfo(): String? {
            return prefs.getString(loginInfoKey(), null)
        }

        fun getLoginInfoMap(): Map<String, String> {
            val json = getLoginInfo().orEmpty()
            if (json.isBlank()) return emptyMap()
            return runCatching {
                val obj = org.json.JSONObject(json)
                val result = LinkedHashMap<String, String>(obj.length())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    result[k] = obj.optString(k, "")
                }
                result
            }.getOrElse { emptyMap() }
        }

        fun removeLoginInfo() {
            prefs.edit().remove(loginInfoKey()).apply()
        }

        private fun sourceVariableKey(): String = "sourceVariable_${getKey()}"
        private fun loginInfoKey(): String = "userInfo_${getKey()}"
        private fun kvKey(key: String): String = "v_${getKey()}_$key"

        private companion object {
            private const val PREFS_NAME = "legado_source_store"
        }
    }
    
    /**
     * Book 对象包装器，提供 Legado 兼容的方法
     * 
     * 支持聚合源等场景中 JavaScript 调用：
     * - book.putVariable(key, value)
     * - book.getVariable(key)
     * - book.setUseReplaceRule(boolean)
     * 
     * 变量持久化：使用 SharedPreferences，key 基于 bookUrl
     */
    class BookWrapper(
        private val book: BookInfo,
        private val context: Context,
        private val sourceKey: String
    ) {
        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        
        // 属性访问器 - Rhino 会自动调用这些 getter 方法
        val bookUrl: String get() = book.bookUrl
        val name: String? get() = book.name
        val author: String? get() = book.author
        val coverUrl: String? get() = book.coverUrl
        val intro: String? get() = book.intro
        val kind: String? get() = book.kind
        val lastChapter: String? get() = book.lastChapter
        val tocUrl: String? get() = book.tocUrl
        val wordCount: String? get() = book.wordCount
        val type: Int? get() = book.type
        
        /**
         * 存储变量（Legado 兼容 + 持久化）
         * JS 中调用：book.putVariable("key", "value")
         * 
         * 同时存储到内存（BookInfo）和持久化存储（SharedPreferences）
         */
        fun putVariable(key: String, value: String?): Boolean {
            // 内存存储
            book.putVariable(key, value)
            // 持久化存储
            prefs.edit().apply {
                if (value == null) {
                    remove(bookVariableKey(key))
                } else {
                    putString(bookVariableKey(key), value)
                }
            }.apply()
            Log.d(TAG, "book.putVariable($key, $value) -> persisted to prefs")
            return true
        }
        
        /**
         * 获取变量（Legado 兼容 + 持久化读取）
         * JS 中调用：book.getVariable("key") 或 book.getVariable("custom")
         * 
         * 优先从内存读取，如果不存在则从持久化存储读取
         */
        fun getVariable(key: String): String {
            // 先从内存获取
            val memValue = book.getVariable(key)
            if (memValue.isNotEmpty()) {
                return memValue
            }
            // 从持久化存储获取
            val persistedValue = prefs.getString(bookVariableKey(key), "") ?: ""
            if (persistedValue.isNotEmpty()) {
                // 同步到内存
                book.putVariable(key, persistedValue)
                Log.d(TAG, "book.getVariable($key) -> loaded from prefs: $persistedValue")
                return persistedValue
            }
            // 聚合源常见：书籍变量（custom）可配置为“全局默认值”，当单书未设置时回退到默认。
            val defaultValue = prefs.getString(bookDefaultKey(key), "") ?: ""
            if (defaultValue.isNotEmpty()) {
                book.putVariable(key, defaultValue)
                Log.d(TAG, "book.getVariable($key) -> loaded default from prefs: $defaultValue")
            }
            return defaultValue
        }
        
        /**
         * 设置是否使用替换规则（Legado 兼容）
         * JS 中调用：book.setUseReplaceRule(false)
         */
        fun setUseReplaceRule(useReplaceRule: Boolean) {
            book.setUseReplaceRule(useReplaceRule)
        }
        
        /**
         * 获取是否使用替换规则
         */
        fun getUseReplaceRule(): Boolean {
            return book.getUseReplaceRule()
        }
        
        /**
         * 设置属性（Legado 兼容）
         */
        fun setProperty(name: String, value: Any?) {
            book.setProperty(name, value)
        }
        
        /**
         * 获取属性（Legado 兼容）
         */
        fun getProperty(propertyName: String): Any? {
            return book.getProperty(propertyName)
        }
        
        // 生成持久化 key，使用 bookUrl 的 MD5 hash 避免 key 过长
        private fun bookVariableKey(key: String): String {
            val urlHash = book.bookUrl.hashCode().toString(16)
            return "bookVar_${urlHash}_$key"
        }

        private fun bookDefaultKey(key: String): String {
            val sourceHash = sourceKey.hashCode().toString(16)
            return "bookVar_default_${sourceHash}_$key"
        }
        
        private companion object {
            private const val TAG = "BookWrapper"
            private const val PREFS_NAME = "legado_book_store"
        }
    }
    
    companion object {
        private const val TAG = "RhinoJavaScriptEngine"

        /**
         * 预处理 Legado 规则中的 JS 片段：
         * - 去掉 `@js:` / `<js>` / `</js>` 包装
         *
         * legado-with-MD3 的 Rhino 执行不会强制包裹 IIFE，也不会强行返回 `result/c`；
         * 返回值应当由脚本最后一个表达式决定（必要时由调用方兜底读取 `result` 变量）。
         */
        fun wrapScript(raw: String): String {
            var script = raw.trim()
            if (script.startsWith("@js:", ignoreCase = true)) {
                script = script.removePrefix("@js:").trim()
            }
            if (script.startsWith("<js>", ignoreCase = true)) {
                script = script.removePrefix("<js>").trim()
            }
            if (script.endsWith("</js>", ignoreCase = true)) {
                script = script.removeSuffix("</js>").trim()
            }
            // 兼容部分三方脚本使用的 ES2019 语法：`catch { ... }`（省略异常变量）。
            // Rhino 在部分版本下不支持该语法，会抛出 SyntaxError。
            script = script.replace(Regex("\\bcatch\\s*\\{"), "catch(e){")
            return script
        }
    }
}
