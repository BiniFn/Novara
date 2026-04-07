package org.skepsun.kototoro.reader.novel.tts.model

import android.net.Uri

enum class TokenType {
    NARRATION,
    DIALOGUE,
    EMPHASIS,
    PAUSE
}

data class Speaker(
    val name: String,
    val voiceId: String
)

data class Token(
    val id: Long,                 // 全局唯一（用于调度）
    val text: String,
    val type: TokenType,
    val range: IntRange,
    val speaker: Speaker? = null,    // 用于多角色广播剧模式
    val durationHintMs: Long? = null // PAUSE 用
)

data class AudioData(
    val uri: Uri,          // 本地文件 or content://
    val durationMs: Long? = null // 可选（用于优化调度）
)

data class TtsSession(
    val id: Long,
    val startTokenIndex: Int
)

data class TtsHttpConfig(
    val name: String = "Legado Source",
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String, // e.g. "{\"text\": \"{{text}}\", \"voice\": \"{{voice}}\"}"
    val voice: String,
    val speed: Float
)

