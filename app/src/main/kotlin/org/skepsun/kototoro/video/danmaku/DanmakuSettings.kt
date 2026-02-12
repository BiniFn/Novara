package org.skepsun.kototoro.video.danmaku

data class DanmakuSettings(
    val enabled: Boolean,
    val sizePercent: Int,
    val speedPercent: Int,
    val opacityPercent: Int,
    val strokePercent: Int,
    val showScroll: Boolean,
    val showTop: Boolean,
    val showBottom: Boolean,
    val maxScrollLines: Int,
    val maxTopLines: Int,
    val maxBottomLines: Int,
    val maxScreenNum: Int,
)
