package org.skepsun.kototoro.core.javascript

import android.content.Context
import android.content.SharedPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import java.net.CookieManager

/**
 * JavaScript 引擎单元测试
 * 
 * 测试基本的 JavaScript 执行、变量传递、返回值处理和异常处理
 */
class RhinoJavaScriptEngineTest : FunSpec({
    
    lateinit var engine: RhinoJavaScriptEngine
    lateinit var mockHttpClient: LegadoHttpClient
    lateinit var mockCookieManager: CookieManager
    lateinit var mockCookieJar: PersistentCookieJar
    lateinit var mockContext: Context
    lateinit var prefs: SharedPreferences
    lateinit var prefsEditor: SharedPreferences.Editor
    lateinit var prefStore: MutableMap<String, String>
    
    beforeTest {
        mockHttpClient = mockk(relaxed = true)
        mockCookieManager = CookieManager()
        mockCookieJar = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        prefsEditor = mockk(relaxed = true)
        prefStore = linkedMapOf()
        every { mockContext.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefs.getString(any(), any()) } answers {
            prefStore[firstArg<String>()] ?: secondArg<String?>()
        }
        every { prefsEditor.putString(any(), any()) } answers {
            prefStore[firstArg<String>()] = secondArg<String>()
            prefsEditor
        }
        every { prefsEditor.remove(any()) } answers {
            prefStore.remove(firstArg<String>())
            prefsEditor
        }
        every { prefsEditor.apply() } answers { Unit }
        engine = RhinoJavaScriptEngine(mockHttpClient, mockCookieManager, mockCookieJar, mockContext)
    }
    
    afterTest {
        engine.dispose()
    }
    
    test("基本的 JavaScript 执行 - 简单表达式") {
        val context = JavaScriptContext()
        val result = engine.execute("1 + 1", context)
        
        result shouldNotBe null
        result shouldBe 2
    }
    
    test("基本的 JavaScript 执行 - 字符串操作") {
        val context = JavaScriptContext()
        val result = engine.execute("'Hello' + ' ' + 'World'", context)
        
        result shouldNotBe null
        result shouldBe "Hello World"
    }
    
    test("变量传递 - result 变量") {
        val context = JavaScriptContext()
        context.setVariable("result", "initial value")
        
        val result = engine.execute("result", context)
        
        result shouldNotBe null
        result.toString() shouldBe "initial value"
    }
    
    test("变量传递 - baseUrl 变量") {
        val context = JavaScriptContext(baseUrl = "https://example.com")
        
        val result = engine.execute("baseUrl + '/path'", context)
        
        result shouldNotBe null
        result shouldBe "https://example.com/path"
    }
    
    test("变量传递 - key 和 page 变量") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com"
        )
        val context = JavaScriptContext.forSearch("test keyword", 1, source)
        
        val result = engine.execute("key + ' page:' + page", context)
        
        result shouldNotBe null
        result shouldBe "test keyword page:1"
    }
    
    test("变量传递 - book 对象") {
        val book = BookInfo(
            bookUrl = "https://example.com/book/123",
            name = "Test Book",
            author = "Test Author"
        )
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com"
        )
        val context = JavaScriptContext.forBookInfo(book, source, "https://example.com")
        
        val result = engine.execute("book.name + ' by ' + book.author", context)
        
        result shouldNotBe null
        result shouldBe "Test Book by Test Author"
    }
    
    test("返回值处理 - 修改 result 变量") {
        val context = JavaScriptContext()
        context.setVariable("result", "original")
        
        val result = engine.execute("result = 'modified'; result", context)
        
        result shouldNotBe null
        result shouldBe "modified"
    }
    
    test("返回值处理 - 返回对象") {
        val context = JavaScriptContext()
        
        val result = engine.execute("({name: 'test', value: 123})", context)
        
        result shouldNotBe null
        result.shouldBeInstanceOf<org.mozilla.javascript.NativeObject>()
    }
    
    test("返回值处理 - 返回数组") {
        val context = JavaScriptContext()
        
        val result = engine.execute("[1, 2, 3, 4, 5]", context)
        
        result shouldNotBe null
        result.shouldBeInstanceOf<org.mozilla.javascript.NativeArray>()
    }
    
    test("异常处理 - 语法错误") {
        val context = JavaScriptContext()
        
        val result = engine.execute("this is invalid javascript", context)
        
        // 应该返回 null 而不是抛出异常
        result shouldBe null
    }
    
    test("异常处理 - 运行时错误") {
        val context = JavaScriptContext()
        
        val result = engine.execute("undefinedVariable.property", context)
        
        // 应该返回 null 而不是抛出异常
        result shouldBe null
    }
    
    test("evaluate 方法 - 简单表达式") {
        val context = JavaScriptContext()
        
        val result = engine.evaluate("2 * 3", context)
        
        result shouldNotBe null
        result shouldBe 6
    }
    
    test("registerGlobalObject - 注册全局对象") {
        val testObject = object {
            fun getValue() = "test value"
        }
        
        engine.registerGlobalObject("testObj", testObject)
        
        val context = JavaScriptContext()
        val result = engine.execute("testObj.getValue()", context)
        
        result shouldNotBe null
        result shouldBe "test value"
    }

    test("JavaScriptContext.javaBridge 应覆盖默认 java 绑定") {
        val bridge = object {
            fun marker() = "custom-java-bridge"
        }
        val context = JavaScriptContext().also {
            it.javaBridge = bridge
        }

        val result = engine.execute("java.marker()", context)

        result shouldBe "custom-java-bridge"
    }

    test("JavaScriptContext.javaBridge 不应覆盖掉默认 LegadoJavaAPI 能力") {
        val bridge = object {
            fun marker() = "custom-java-bridge"
        }
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
        )
        val context = JavaScriptContext(source = source).also {
            it.javaBridge = bridge
        }

        val result = engine.execute("java.marker() + '|' + java.getTag()", context)

        result shouldBe "custom-java-bridge|Test Source"
    }

    test("运行时 bridge 与默认 LegadoJavaAPI 应在 Rhino 中组合生效") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
        )
        val bridge = object {
            fun isPost() = true
            fun getTag() = "Test Source"
        }
        val context = JavaScriptContext(
            baseUrl = "https://example.com",
            source = source,
        ).also {
            it.javaBridge = bridge
        }

        val result = engine.execute("java.isPost() + '|' + java.getTag() + '|' + java.getSource().bookSourceName", context)

        result shouldBe "true|Test Source|Test Source"
    }
    
    test("复杂脚本 - 多行代码") {
        val context = JavaScriptContext()
        
        val script = """
            var items = ['a', 'b', 'c'];
            var output = '';
            for (var i = 0; i < items.length; i++) {
                output += items[i];
            }
            output;
        """.trimIndent()
        
        val result = engine.execute(script, context)
        
        result shouldNotBe null
        result.toString() shouldBe "abc"
    }
    
    test("真实 Legado 场景 - 搜索 URL 生成") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com"
        )
        val context = JavaScriptContext.forSearch("测试", 1, source)
        
        val script = """
            var encodedKey = encodeURIComponent(key);
            source.bookSourceUrl + '/search?q=' + encodedKey + '&page=' + page;
        """.trimIndent()
        
        val result = engine.execute(script, context)
        
        result shouldNotBe null
        result.toString() shouldBe "https://example.com/search?q=%E6%B5%8B%E8%AF%95&page=1"
    }

    test("source 应暴露 MD3 风格基础方法") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
        )
        val context = JavaScriptContext(source = source)

        val result = engine.execute(
            "source.getSource().getTag() + '|' + source.getSourceType() + '|' + source.getSource().getKey()",
            context,
        )

        result shouldBe "Test Source|0|https://example.com"
    }

    test("source.putVariable(variable) 应与 getVariable 对齐 MD3") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
        )
        val context = JavaScriptContext(source = source)

        val result = engine.execute(
            "source.putVariable('token=abc'); source.getVariable()",
            context,
        )

        result shouldBe "token=abc"
        prefStore["sourceVariable_https://example.com"] shouldBe "token=abc"
    }

    test("source.getHeaderMap 应补默认 User-Agent 并合并登录头") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            header = """{"Referer":"https://ref.example.com"}""",
        )
        prefStore["loginHeader_https://example.com"] = """{"Cookie":"cf_clearance=ok"}"""
        val context = JavaScriptContext(source = source)

        val result = engine.execute(
            "source.getHeaderMap(true).get('User-Agent') + '|' + source.getHeaderMap(true).get('Referer') + '|' + source.getHeaderMap(true).get('Cookie')",
            context,
        )

        result.toString() shouldContain "|https://ref.example.com|cf_clearance=ok"
        result.toString() shouldContain "Mozilla/5.0"
    }

    test("source.getLoginInfoMap 应按 loginUi 生成默认值并持久化") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            loginUi = """
                [
                  {"name":"username","default":"guest"},
                  {"name":"password","default":"123456"},
                  {"name":"submit","type":"button","default":"ignored"}
                ]
            """.trimIndent(),
        )
        val context = JavaScriptContext(source = source)

        val result = engine.execute(
            "source.getLoginInfoMap().get('username') + '|' + source.getLoginInfoMap().get('password')",
            context,
        )

        result shouldBe "guest|123456"
        prefStore["userInfo_https://example.com"] shouldContain """"username":"guest""""
        prefStore["userInfo_https://example.com"] shouldContain """"password":"123456""""
    }

    test("source.putLoginHeader 和 removeLoginHeader 应同步 cookie 副作用") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
        )
        every { mockCookieJar.getCookieHeader("https://example.com") } returns ""
        val context = JavaScriptContext(source = source)

        engine.execute(
            """source.putLoginHeader('{"Cookie":"cf_clearance=abc; session=1"}')""",
            context,
        ) shouldBe true
        engine.execute("source.removeLoginHeader()", context).toString() shouldBe "undefined"

        verify(exactly = 1) {
            mockCookieJar.setCookies(
                eq("https://example.com"),
                match { cookies ->
                    cookies.any { it.name == "cf_clearance" && it.value == "abc" } &&
                        cookies.any { it.name == "session" && it.value == "1" }
                },
            )
        }
        verify(exactly = 1) {
            mockCookieJar.removeCookies("https://example.com")
        }
    }
})
