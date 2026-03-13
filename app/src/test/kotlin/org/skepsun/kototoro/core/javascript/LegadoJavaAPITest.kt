package org.skepsun.kototoro.core.javascript

import android.content.Context
import android.provider.Settings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import java.net.CookieManager

/**
 * 测试 LegadoJavaAPI 类
 * 
 * 验证所有 Legado API 方法的正确性
 */
class LegadoJavaAPITest : FunSpec({
    
    lateinit var httpClient: LegadoHttpClient
    lateinit var cookieManager: CookieManager
    lateinit var context: Context
    lateinit var api: LegadoJavaAPI
    
    beforeTest {
        httpClient = mockk()
        cookieManager = mockk()
        context = mockk(relaxed = true)
        
        // Mock Settings.Secure for androidId()
        mockkStatic(Settings.Secure::class)
        every { 
            Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) 
        } returns "test_android_id"
        
        api = LegadoJavaAPI(httpClient, cookieManager, context)
    }
    
    test("ajax GET request") {
        // Given
        val url = "https://example.com/test"
        val responseBody = "Test response"
        val mockResponse = createMockResponse(url, responseBody)
        
        coEvery { httpClient.get(url, any(), any()) } returns mockResponse
        
        // When
        val result = api.ajax(url)
        
        // Then
        result shouldBe responseBody
    }
    
    test("ajax POST request with options") {
        // Given
        val url = "https://example.com/test"
        val responseBody = "Post response"
        val mockResponse = createMockResponse(url, responseBody)
        val options = mapOf(
            "method" to "POST",
            "body" to "key1=value1&key2=value2",
            "headers" to mapOf("Content-Type" to "application/x-www-form-urlencoded")
        )
        
        coEvery { httpClient.post(url, any<Map<String, String>>(), any(), any()) } returns mockResponse
        
        // When
        val result = api.ajax(url, options)
        
        // Then
        result shouldBe responseBody
    }
    
    test("setContent and getElement") {
        // Given
        val html = """
            <html>
                <body>
                    <div class="title">Test Title</div>
                    <div class="content">Test Content</div>
                </body>
            </html>
        """.trimIndent()
        
        // When
        api.setContent(html)
        val elements = api.getElement(".title")
        
        // Then
        elements.size shouldBe 1
        elements.first()?.text() shouldBe "Test Title"
    }
    
    test("base64Encode") {
        // Given
        val input = "Hello, World!"
        
        // When
        val result = api.base64Encode(input)
        
        // Then
        result shouldBe "SGVsbG8sIFdvcmxkIQ=="
    }
    
    test("base64Decode") {
        // Given
        val input = "SGVsbG8sIFdvcmxkIQ=="
        
        // When
        val result = api.base64Decode(input)
        
        // Then
        result shouldBe "Hello, World!"
    }
    
    test("hexDecodeToString") {
        // Given
        val hex = "48656c6c6f" // "Hello" in hex
        
        // When
        val result = api.hexDecodeToString(hex)
        
        // Then
        result shouldBe "Hello"
    }
    
    test("timeFormat") {
        // Given
        val timestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val format = "yyyy-MM-dd"
        
        // When
        val result = api.timeFormat(timestamp, format, "UTC")
        
        // Then
        result shouldBe "2021-01-01"
    }
    
    test("timeFormatUTC with positive offset") {
        // Given
        val timestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val format = "yyyy-MM-dd HH:mm:ss"
        val offset = 8 // GMT+8
        
        // When
        val result = api.timeFormatUTC(timestamp, format, offset)
        
        // Then
        result shouldStartWith "2021-01-01"
    }
    
    test("timeFormatUTC with negative offset") {
        // Given
        val timestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val format = "yyyy-MM-dd HH:mm:ss"
        val offset = -5 // GMT-5
        
        // When
        val result = api.timeFormatUTC(timestamp, format, offset)
        
        // Then
        result shouldStartWith "2020-12-31"
    }
    
    test("androidId") {
        // When
        val result = api.androidId()
        
        // Then
        result shouldBe "test_android_id"
    }
    
    test("t2s returns original text") {
        // Given
        val input = "繁體中文"
        
        // When
        val result = api.t2s(input)
        
        // Then
        // Currently not implemented, should return original text
        result shouldBe input
    }
    
    test("s2t returns original text") {
        // Given
        val input = "简体中文"
        
        // When
        val result = api.s2t(input)
        
        // Then
        // Currently not implemented, should return original text
        result shouldBe input
    }
})

/**
 * Helper function to create a mock HTTP response
 */
private fun createMockResponse(url: String, body: String): Response {
    return Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody("text/html".toMediaType()))
        .build()
}
