package org.skepsun.kototoro.video.danmaku

import android.graphics.Color
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER

class DanmakuListParser(
    private val items: List<DanmakuItem>,
) {
    fun toTextDataList(): List<TextData> {
        if (items.isEmpty()) return emptyList()
        return items.map { item ->
            TextData().apply {
                text = item.message
                showAtTime = item.timeMs
                layerType = when (item.type) {
                    DanmakuType.SCROLL -> LAYER_TYPE_SCROLL
                    DanmakuType.TOP -> LAYER_TYPE_TOP_CENTER
                    DanmakuType.BOTTOM -> LAYER_TYPE_BOTTOM_CENTER
                }
                textColor = item.color
                textStrokeColor = Color.BLACK
            }
        }
    }
}
