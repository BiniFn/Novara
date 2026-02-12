package org.skepsun.kototoro.video.danmaku

data class DanmakuItem(
    val message: String,
    val timeMs: Long,
    val type: DanmakuType,
    val color: Int,
)

enum class DanmakuType {
    SCROLL,
    TOP,
    BOTTOM,
}
