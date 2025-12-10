package org.skepsun.kototoro.core.javascript

import android.content.Context
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import java.net.CookieManager

/**
 * JavaScript 安全测试
 * 
 * 测试超时控制、异常处理、资源限制等安全措施
 * 
 * **使用恶意或有问题的 JavaScript 代码进行测试**
 * 
 * 验证需求: 18.1-18.5
 */
class JavaScriptSecurityTest {
    
    private lateinit var httpClient: LegadoHttpClient
    private lateinit var cookieManager: CookieManager
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var context: Context
    private lateinit var baseEngine: RhinoJavaScriptEngine
    
    @Before
    fun setup() {
        httpClient = mockk(relaxed = true)
        cookieManager = CookieManager()
        cookieJar = mockk(relaxed = true)
        context = mockk(relaxed = true)
        
        baseEngine = RhinoJavaScriptEngine(
            httpClient = httpClient,
            cookieManager = cookieManager,
            cookieJar = cookieJar,
            androidContext = context
        )
    }
    
    @After
    fun tearDown() {
        baseEngine.dispose()
    }
    
    // ========== 超时控制测试 ==========
    
    @Test(expected = JavaScriptTimeoutException::class)
    fun `test timeout control - infinite loop`() {
        // 创建带超时的引擎（使用较短的超时时间以加快测试）
        val timeoutEngine = TimeoutJavaScriptEngine(baseEngine, timeoutMillis = 1000)
        val jsContext = JavaScriptContext()
        
        // 无限循环代码
        val infiniteLoopScript = """
            while(true) {
                // 无限循环
            }
        """.trimIndent()
        
        // 应该抛出超时异常
        timeoutEngine.execute(infiniteLoopScript, jsContext)
    }
    
    @Test(expected = JavaScriptTimeoutException::class)
    fun `test timeout control - long running script`() {
        val timeoutEngine = TimeoutJavaScriptEngine(baseEngine, timeoutMillis = 500)
        val jsContext = JavaScriptContext()
        
        // 长时间运行的脚本
        val longRunningScript = """
            var sum = 0;
            for (var i = 0; i < 100000000; i++) {
                sum += i;
            }
            sum;
        """.trimIndent()
        
        // 应该抛出超时异常
        timeoutEngine.execute(longRunningScript, jsContext)
    }
    
    @Test
    fun `test timeout control - normal script completes`() {
        val timeoutEngine = TimeoutJavaScriptEngine(baseEngine, timeoutMillis = 5000)
        val jsContext = JavaScriptContext()
        
        // 正常的快速脚本
        val normalScript = """
            var result = 1 + 2 + 3;
            result;
        """.trimIndent()
        
        // 应该正常完成
        val result = timeoutEngine.execute(normalScript, jsContext)
        assertNotNull(result)
        assertEquals(6.0, (result as Number).toDouble(), 0.001)
    }
    
    // ========== 异常捕获测试 ==========
    
    @Test
    fun `test exception handling - syntax error returns null`() {
        val safeEngine = SafeJavaScriptEngine(baseEngine)
        val jsContext = JavaScriptContext()
        
        // 语法错误的代码
        val syntaxErrorScript = """
            var x = ;
        """.trimIndent()
        
        // 应该返回 null 而不是崩溃
        val result = safeEngine.execute(syntaxErrorScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test exception handling - runtime error returns null`() {
        val safeEngine = SafeJavaScriptEngine(baseEngine)
        val jsContext = JavaScriptContext()
        
        // 运行时错误的代码
        val runtimeErrorScript = """
            var obj = null;
            obj.property;
        """.trimIndent()
        
        // 应该返回 null 而不是崩溃
        val result = safeEngine.execute(runtimeErrorScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test exception handling - undefined variable returns null`() {
        val safeEngine = SafeJavaScriptEngine(baseEngine)
        val jsContext = JavaScriptContext()
        
        // 访问未定义的变量
        val undefinedVarScript = """
            undefinedVariable.method();
        """.trimIndent()
        
        // 应该返回 null 而不是崩溃
        val result = safeEngine.execute(undefinedVarScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test exception handling - timeout exception returns null`() {
        val timeoutEngine = TimeoutJavaScriptEngine(baseEngine, timeoutMillis = 500)
        val safeEngine = SafeJavaScriptEngine(timeoutEngine)
        val jsContext = JavaScriptContext()
        
        // 无限循环
        val infiniteLoopScript = """
            while(true) {}
        """.trimIndent()
        
        // 应该返回 null 而不是崩溃
        val result = safeEngine.execute(infiniteLoopScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test exception handling - normal script returns result`() {
        val safeEngine = SafeJavaScriptEngine(baseEngine)
        val jsContext = JavaScriptContext()
        
        // 正常的脚本
        val normalScript = """
            var x = 10;
            var y = 20;
            x + y;
        """.trimIndent()
        
        // 应该返回正确的结果
        val result = safeEngine.execute(normalScript, jsContext)
        assertNotNull(result)
        assertEquals(30.0, (result as Number).toDouble(), 0.001)
    }
    
    // ========== 资源限制测试 ==========
    
    @Test
    fun `test resource limit - memory usage check`() {
        // 使用较低的内存限制以便测试
        val resourceLimitedEngine = ResourceLimitedJavaScriptEngine(baseEngine, maxMemoryMB = 10)
        
        // 获取内存使用情况
        val memoryUsage = resourceLimitedEngine.getMemoryUsage()
        
        // 验证内存使用信息
        assertTrue(memoryUsage.usedMemoryMB >= 0)
        assertTrue(memoryUsage.maxMemoryMB > 0)
        assertTrue(memoryUsage.totalMemoryMB > 0)
        assertTrue(memoryUsage.freeMemoryMB >= 0)
        assertEquals(10L, memoryUsage.limitMB)
    }
    
    @Test
    fun `test resource limit - normal script executes`() {
        val resourceLimitedEngine = ResourceLimitedJavaScriptEngine(baseEngine, maxMemoryMB = 100)
        val jsContext = JavaScriptContext()
        
        // 正常的脚本
        val normalScript = """
            var arr = [];
            for (var i = 0; i < 100; i++) {
                arr.push(i);
            }
            arr.length;
        """.trimIndent()
        
        // 应该正常执行
        val result = resourceLimitedEngine.execute(normalScript, jsContext)
        assertNotNull(result)
        assertEquals(100.0, (result as Number).toDouble(), 0.001)
    }
    
    // ========== 语法检查测试 ==========
    
    @Test
    fun `test syntax validation - valid script`() {
        val result = JavaScriptSyntaxValidator.validate("""
            var x = 10;
            var y = 20;
            x + y;
        """.trimIndent())
        
        assertTrue(result.isValid)
        assertNull(result.message)
    }
    
    @Test
    fun `test syntax validation - syntax error detected`() {
        val result = JavaScriptSyntaxValidator.validate("""
            var x = ;
        """.trimIndent())
        
        assertTrue(!result.isValid)
        assertNotNull(result.message)
        assertNotNull(result.lineNumber)
    }
    
    @Test
    fun `test syntax validation - missing bracket`() {
        val result = JavaScriptSyntaxValidator.validate("""
            function test() {
                var x = 10;
            // 缺少闭合括号
        """.trimIndent())
        
        assertTrue(!result.isValid)
        assertNotNull(result.message)
    }
    
    @Test(expected = JavaScriptSyntaxException::class)
    fun `test syntax checking engine - strict mode throws exception`() {
        val syntaxCheckingEngine = SyntaxCheckingJavaScriptEngine(baseEngine, strictMode = true)
        val jsContext = JavaScriptContext()
        
        // 语法错误的代码
        val syntaxErrorScript = """
            var x = ;
        """.trimIndent()
        
        // 严格模式应该抛出异常
        syntaxCheckingEngine.execute(syntaxErrorScript, jsContext)
    }
    
    @Test
    fun `test syntax checking engine - non-strict mode continues`() {
        val syntaxCheckingEngine = SyntaxCheckingJavaScriptEngine(baseEngine, strictMode = false)
        val jsContext = JavaScriptContext()
        
        // 语法错误的代码
        val syntaxErrorScript = """
            var x = ;
        """.trimIndent()
        
        // 非严格模式应该继续执行（虽然会失败，但不抛出语法异常）
        // 注意：这里会因为实际执行失败而返回 null 或抛出其他异常
        try {
            val result = syntaxCheckingEngine.execute(syntaxErrorScript, jsContext)
            // 如果没有抛出异常，结果应该是 null
            assertNull(result)
        } catch (e: Exception) {
            // 如果抛出异常，不应该是语法异常
            assertTrue(e !is JavaScriptSyntaxException)
        }
    }
    
    // ========== 组合安全措施测试 ==========
    
    @Test
    fun `test combined security - timeout and safe`() {
        val secureEngine = baseEngine.withTimeout(1000)
        val jsContext = JavaScriptContext()
        
        // 无限循环
        val infiniteLoopScript = """
            while(true) {}
        """.trimIndent()
        
        // 应该返回 null（超时后被安全捕获）
        val result = secureEngine.execute(infiniteLoopScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test combined security - all protections`() {
        // 组合所有安全措施
        val secureEngine = baseEngine
            .withResourceLimit(maxMemoryMB = 50)
            .withTimeout(timeoutMillis = 5000)
        
        val jsContext = JavaScriptContext()
        
        // 正常的脚本应该能够执行
        val normalScript = """
            var result = 0;
            for (var i = 0; i < 100; i++) {
                result += i;
            }
            result;
        """.trimIndent()
        
        val result = secureEngine.execute(normalScript, jsContext)
        assertNotNull(result)
        assertEquals(4950.0, (result as Number).toDouble(), 0.001)
    }
    
    // ========== 恶意代码测试 ==========
    
    @Test
    fun `test malicious code - recursive function`() {
        val safeEngine = baseEngine.withTimeout(1000)
        val jsContext = JavaScriptContext()
        
        // 无限递归
        val recursiveScript = """
            function recursive() {
                recursive();
            }
            recursive();
        """.trimIndent()
        
        // 应该被超时或栈溢出保护捕获
        val result = safeEngine.execute(recursiveScript, jsContext)
        assertNull(result)
    }
    
    @Test
    fun `test malicious code - memory bomb`() {
        val safeEngine = baseEngine.withTimeout(2000)
        val jsContext = JavaScriptContext()
        
        // 尝试创建大量对象
        val memoryBombScript = """
            var arr = [];
            for (var i = 0; i < 1000000; i++) {
                arr.push({data: new Array(1000)});
            }
        """.trimIndent()
        
        // 应该被超时或内存限制保护
        val result = safeEngine.execute(memoryBombScript, jsContext)
        // 可能返回 null（超时）或抛出内存异常
        // 无论如何，不应该崩溃
    }
}
