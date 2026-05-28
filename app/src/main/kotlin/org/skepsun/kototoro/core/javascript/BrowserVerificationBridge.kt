package org.skepsun.kototoro.core.javascript

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 为 legado 的 startBrowserAwait 提供进程内阻塞等待能力。
 *
 * 行为目标对齐 MD3：
 * - JS 线程发起浏览器验证后阻塞等待结果
 * - 浏览器页关闭或完成验证时回传当前 URL/HTML
 */
object BrowserVerificationBridge {

    data class Result(
        val url: String,
        val html: String,
    )

    private val pending = ConcurrentHashMap<String, CompletableFuture<Result>>()

    fun register(): String {
        val token = UUID.randomUUID().toString()
        pending[token] = CompletableFuture()
        return token
    }

    fun await(token: String, timeoutSeconds: Long): Result? {
        val future = pending[token] ?: return null
        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        } finally {
            pending.remove(token)
        }
    }

    fun complete(token: String, result: Result) {
        pending.remove(token)?.complete(result)
    }

    fun cancel(token: String) {
        pending.remove(token)?.cancel(true)
    }
}
