package org.skepsun.kototoro.core.javascript

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Cookie
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar

/**
 * 测试 LegadoCookieAPI
 * 
 * 验证 Cookie 管理功能的正确性，包括:
 * - Cookie 的设置和获取
 * - Cookie 的删除
 * - Cookie 字符串和 Map 的转换
 * - Cookie 的持久化
 */
class LegadoCookieAPITest : FunSpec({
    
    lateinit var cookieJar: PersistentCookieJar
    lateinit var cookieAPI: LegadoCookieAPI
    
    val testUrl = "https://example.com"
    val testDomain = "example.com"
    
    beforeTest {
        cookieJar = mockk(relaxed = true)
        cookieAPI = LegadoCookieAPI(cookieJar)
    }
    
    test("getCookie returns empty string when no cookies exist") {
        // Given
        every { cookieJar.getCookies(testUrl) } returns emptyList()
        
        // When
        val result = cookieAPI.getCookie(testUrl)
        
        // Then
        result shouldBe ""
    }
    
    test("getCookie returns formatted cookie string") {
        // Given
        val cookies = listOf(
            createCookie("session", "abc123", testDomain),
            createCookie("user", "john", testDomain),
            createCookie("token", "xyz789", testDomain)
        )
        every { cookieJar.getCookies(testUrl) } returns cookies
        
        // When
        val result = cookieAPI.getCookie(testUrl)
        
        // Then
        result shouldBe "session=abc123; user=john; token=xyz789"
    }
    
    test("setCookie with null or blank cookie does nothing") {
        // When
        cookieAPI.setCookie(testUrl, null)
        cookieAPI.setCookie(testUrl, "")
        cookieAPI.setCookie(testUrl, "   ")
        
        // Then
        verify(exactly = 0) { cookieJar.setCookies(any(), any()) }
    }
    
    test("setCookie parses and stores simple cookie format") {
        // Given
        val cookieString = "session=abc123; user=john"
        
        // When
        cookieAPI.setCookie(testUrl, cookieString)
        
        // Then
        verify {
            cookieJar.setCookies(
                eq(testUrl),
                match { cookies ->
                    cookies.size == 2 &&
                    cookies.any { it.name == "session" && it.value == "abc123" } &&
                    cookies.any { it.name == "user" && it.value == "john" }
                }
            )
        }
    }
    
    test("replaceCookie sets cookie when no existing cookie") {
        // Given
        every { cookieJar.getCookies(testUrl) } returns emptyList()
        val newCookie = "session=abc123"
        
        // When
        cookieAPI.replaceCookie(testUrl, newCookie)
        
        // Then
        verify { cookieJar.setCookies(eq(testUrl), any()) }
    }
    
    test("replaceCookie merges with existing cookies") {
        // Given
        val existingCookies = listOf(
            createCookie("session", "old123", testDomain),
            createCookie("user", "john", testDomain)
        )
        every { cookieJar.getCookies(testUrl) } returns existingCookies
        val newCookie = "session=new456; token=xyz789"
        
        // When
        cookieAPI.replaceCookie(testUrl, newCookie)
        
        // Then
        verify {
            cookieJar.setCookies(
                eq(testUrl),
                match { cookies ->
                    // Should have 3 cookies: session (updated), user (kept), token (new)
                    cookies.size == 3 &&
                    cookies.any { it.name == "session" && it.value == "new456" } &&
                    cookies.any { it.name == "user" && it.value == "john" } &&
                    cookies.any { it.name == "token" && it.value == "xyz789" }
                }
            )
        }
    }
    
    test("removeCookie removes all cookies for URL") {
        // When
        cookieAPI.removeCookie(testUrl)
        
        // Then
        verify { cookieJar.removeCookies(testUrl) }
    }
    
    test("cookieToMap converts cookie string to map") {
        // Given
        val cookieString = "session=abc123; user=john; token=xyz789"
        
        // When
        val result = cookieAPI.cookieToMap(cookieString)
        
        // Then
        result.size shouldBe 3
        result["session"] shouldBe "abc123"
        result["user"] shouldBe "john"
        result["token"] shouldBe "xyz789"
    }
    
    test("cookieToMap handles empty string") {
        // When
        val result = cookieAPI.cookieToMap("")
        
        // Then
        result.isEmpty() shouldBe true
    }
    
    test("cookieToMap ignores null values") {
        // Given
        val cookieString = "session=abc123; empty=null; user=john"
        
        // When
        val result = cookieAPI.cookieToMap(cookieString)
        
        // Then
        result.size shouldBe 2
        result["session"] shouldBe "abc123"
        result["user"] shouldBe "john"
        result shouldNotContainKey "empty"
    }
    
    test("cookieToMap handles whitespace") {
        // Given
        val cookieString = "  session = abc123 ;  user = john  "
        
        // When
        val result = cookieAPI.cookieToMap(cookieString)
        
        // Then
        result.size shouldBe 2
        result["session"] shouldBe "abc123"
        result["user"] shouldBe "john"
    }
    
    test("mapToCookie converts map to cookie string") {
        // Given
        val cookieMap = mapOf(
            "session" to "abc123",
            "user" to "john",
            "token" to "xyz789"
        )
        
        // When
        val result = cookieAPI.mapToCookie(cookieMap)
        
        // Then
        // Order may vary, so check all parts are present
        result shouldContain "session=abc123"
        result shouldContain "user=john"
        result shouldContain "token=xyz789"
        result shouldContain "; "
    }
    
    test("mapToCookie returns empty string for null map") {
        // When
        val result = cookieAPI.mapToCookie(null)
        
        // Then
        result shouldBe ""
    }
    
    test("mapToCookie returns empty string for empty map") {
        // When
        val result = cookieAPI.mapToCookie(emptyMap())
        
        // Then
        result shouldBe ""
    }
    
    test("getKey returns cookie value by key") {
        // Given
        val cookies = listOf(
            createCookie("session", "abc123", testDomain),
            createCookie("user", "john", testDomain)
        )
        every { cookieJar.getCookies(testUrl) } returns cookies
        
        // When
        val result = cookieAPI.getKey(testUrl, "session")
        
        // Then
        result shouldBe "abc123"
    }
    
    test("getKey returns empty string for non-existent key") {
        // Given
        val cookies = listOf(createCookie("session", "abc123", testDomain))
        every { cookieJar.getCookies(testUrl) } returns cookies
        
        // When
        val result = cookieAPI.getKey(testUrl, "nonexistent")
        
        // Then
        result shouldBe ""
    }
    
    test("round trip conversion preserves cookie data") {
        // Given
        val originalCookieString = "session=abc123; user=john; token=xyz789"
        
        // When
        val cookieMap = cookieAPI.cookieToMap(originalCookieString)
        val reconstructedCookieString = cookieAPI.mapToCookie(cookieMap)
        val finalMap = cookieAPI.cookieToMap(reconstructedCookieString)
        
        // Then
        finalMap shouldBe cookieMap
    }
    
    test("setCookie handles complex cookie with attributes") {
        // Given
        val cookieString = "session=abc123; Domain=example.com; Path=/; Secure; HttpOnly"
        
        // When
        cookieAPI.setCookie(testUrl, cookieString)
        
        // Then
        verify {
            cookieJar.setCookies(
                eq(testUrl),
                match { cookies ->
                    cookies.isNotEmpty() &&
                    cookies.any { it.name == "session" && it.value == "abc123" }
                }
            )
        }
    }
    
    test("cookie persistence scenario - set, get, replace, remove") {
        // Scenario: Simulating a real Legado book source Cookie workflow
        
        // Step 1: Set initial cookies
        val initialCookies = "session=initial123; user=john"
        cookieAPI.setCookie(testUrl, initialCookies)
        
        // Step 2: Mock getting cookies back
        val storedCookies = listOf(
            createCookie("session", "initial123", testDomain),
            createCookie("user", "john", testDomain)
        )
        every { cookieJar.getCookies(testUrl) } returns storedCookies
        
        // Step 3: Get cookies
        val retrieved = cookieAPI.getCookie(testUrl)
        retrieved shouldBe "session=initial123; user=john"
        
        // Step 4: Replace session cookie
        val updatedCookies = listOf(
            createCookie("session", "updated456", testDomain),
            createCookie("user", "john", testDomain),
            createCookie("token", "xyz789", testDomain)
        )
        every { cookieJar.getCookies(testUrl) } returns updatedCookies
        
        cookieAPI.replaceCookie(testUrl, "session=updated456; token=xyz789")
        
        // Step 5: Verify replacement
        val afterReplace = cookieAPI.getCookie(testUrl)
        afterReplace shouldContain "session=updated456"
        afterReplace shouldContain "user=john"
        afterReplace shouldContain "token=xyz789"
        
        // Step 6: Remove all cookies
        cookieAPI.removeCookie(testUrl)
        verify { cookieJar.removeCookies(testUrl) }
    }
})

/**
 * Helper function to create a Cookie object
 */
private fun createCookie(name: String, value: String, domain: String): Cookie {
    return Cookie.Builder()
        .name(name)
        .value(value)
        .domain(domain)
        .path("/")
        .build()
}
