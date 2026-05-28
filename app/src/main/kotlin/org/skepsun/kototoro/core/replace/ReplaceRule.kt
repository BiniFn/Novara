package org.skepsun.kototoro.core.replace

import kotlinx.serialization.Serializable

/**
 * Global text replacement rule, compatible with Legado replace rule JSON format.
 */
@Serializable
data class ReplaceRule(
    val id: Long = 0L,
    val name: String = "",
    val group: String? = null,
    val pattern: String = "",
    val replacement: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = true,
    val isEnabled: Boolean = true,
    val isRegex: Boolean = true,
    val timeoutMillisecond: Long = 3000L,
    val order: Int = 0,
) {
    val scope: Scope
        get() = when {
            scopeTitle && scopeContent -> Scope.BOTH
            scopeTitle -> Scope.TITLE
            else -> Scope.CONTENT
        }

    /** Pre-compiled regex, lazy to fail only on first use. */
    val regex: Regex? by lazy {
        if (!isRegex || pattern.isBlank()) return@lazy null
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun isValid(): Boolean {
        if (pattern.isBlank()) return false
        if (isRegex) {
            val r = regex ?: return false
            if (pattern.endsWith('|') && !pattern.endsWith("\\|")) return false
        }
        return true
    }

    fun apply(text: String): String {
        if (!isEnabled || pattern.isBlank()) return text
        return try {
            if (isRegex) {
                val r = regex ?: return text
                if (timeoutMillisecond > 0) {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeout(timeoutMillisecond) {
                            r.replace(text, replacement)
                        }
                    }
                } else {
                    r.replace(text, replacement)
                }
            } else {
                text.replace(pattern, replacement)
            }
        } catch (_: Exception) {
            text
        }
    }

    enum class Scope { TITLE, CONTENT, BOTH }
}
