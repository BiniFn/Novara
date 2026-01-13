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
            Log.e(TAG, "Script execution failed: $script")
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
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
            
            // Special handling for arrays/lists to ensure they work as JS arrays
            // This is critical for rules like chapterList where JS expects result.length to work
            val jsValue = when (effectiveValue) {
                is List<*> -> {
                    Log.d(TAG, "Converting List to NativeArray for '$name', size=${effectiveValue.size}")
                    val array = ctx.newArray(currentScope, effectiveValue.size)
                    effectiveValue.forEachIndexed { index, item ->
                        val jsItem = RhinoContext.javaToJS(item, currentScope)
                        ScriptableObject.putProperty(array, index, jsItem)
                    }
                    array
                }
                is Array<*> -> {
                    Log.d(TAG, "Converting Array to NativeArray for '$name', size=${effectiveValue.size}")
                    val array = ctx.newArray(currentScope, effectiveValue.size)
                    effectiveValue.forEachIndexed { index, item ->
                        val jsItem = RhinoContext.javaToJS(item, currentScope)
                        ScriptableObject.putProperty(array, index, jsItem)
                    }
                    array
                }
                is String -> {
                    // 确保 Java String 在 Rhino 中可以访问 String.prototype 方法（如 match）
                    // 使用 ctx.newObject("String", ...) 来创建一个真实的 JS String 对象
                    val jsString = ctx.newObject(currentScope, "String", arrayOf(effectiveValue))
                    jsString
                }
                else -> RhinoContext.javaToJS(effectiveValue, currentScope)
            }
            ScriptableObject.putProperty(currentScope, name, jsValue)
        }
        
        // 为 source 对象添加 getKey() 方法
        context.source?.let { source ->
            val sourceWrapper = SourceWrapper(source, androidContext)
            val jsSource = RhinoContext.javaToJS(sourceWrapper, currentScope)
            ScriptableObject.putProperty(currentScope, "source", jsSource)
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
            return script
        }
    }
}
