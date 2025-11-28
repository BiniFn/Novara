package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit

/**
 * 小说阅读器设置
 */
data class NovelReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Float = 8f,
    val marginHorizontal: Int = 24,
    val marginVertical: Int = 16,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val textDirection: TextDirection = TextDirection.LTR,
    val enableDualPage: Boolean = true,
    val enableFullscreen: Boolean = false,  // 默认不全屏，显示状态栏
    val showFooter: Boolean = true,  // 显示页脚（进度和章节名）
) {

    fun save(context: Context) {
        getPrefs(context).edit {
            putFloat(KEY_FONT_SIZE, fontSizeSp)
            putFloat(KEY_LINE_SPACING, lineSpacing)
            putFloat(KEY_PARAGRAPH_SPACING, paragraphSpacing)
            putInt(KEY_MARGIN_HORIZONTAL, marginHorizontal)
            putInt(KEY_MARGIN_VERTICAL, marginVertical)
            putString(KEY_READING_MODE, readingMode.name)
            putString(KEY_TEXT_DIRECTION, textDirection.name)
            putBoolean(KEY_DUAL_PAGE, enableDualPage)
            putBoolean(KEY_FULLSCREEN, enableFullscreen)
            putBoolean(KEY_SHOW_FOOTER, showFooter)
        }
    }

    companion object {
        private const val PREF_NAME = "novel_reader_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_PARAGRAPH_SPACING = "paragraph_spacing"
        private const val KEY_MARGIN_HORIZONTAL = "margin_horizontal"
        private const val KEY_MARGIN_VERTICAL = "margin_vertical"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_TEXT_DIRECTION = "text_direction"
        private const val KEY_DUAL_PAGE = "dual_page"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_SHOW_FOOTER = "show_footer"

        fun load(context: Context): NovelReaderSettings {
            val prefs = getPrefs(context)
            return NovelReaderSettings(
                fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 18f),
                lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.5f),
                paragraphSpacing = prefs.getFloat(KEY_PARAGRAPH_SPACING, 8f),
                marginHorizontal = prefs.getInt(KEY_MARGIN_HORIZONTAL, 24),
                marginVertical = prefs.getInt(KEY_MARGIN_VERTICAL, 16),
                readingMode = runCatching {
                    ReadingMode.valueOf(prefs.getString(KEY_READING_MODE, null) ?: ReadingMode.PAGED.name)
                }.getOrDefault(ReadingMode.PAGED),
                textDirection = runCatching {
                    TextDirection.valueOf(prefs.getString(KEY_TEXT_DIRECTION, null) ?: TextDirection.LTR.name)
                }.getOrDefault(TextDirection.LTR),
                enableDualPage = prefs.getBoolean(KEY_DUAL_PAGE, true),
                enableFullscreen = prefs.getBoolean(KEY_FULLSCREEN, false),
                showFooter = prefs.getBoolean(KEY_SHOW_FOOTER, true),
            )
        }

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }
}

/**
 * 阅读模式
 */
enum class ReadingMode {
    PAGED,      // 分页模式
    SCROLL,     // 滚动模式
    VERTICAL,   // 竖排模式（从右到左翻页）
}

/**
 * 文本方向
 */
enum class TextDirection {
    LTR,  // 从左到右
    RTL,  // 从右到左
}
