package org.skepsun.kototoro.reader.novel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit

/**
 * 小说阅读器设置
 */
data class NovelReaderSettings(
    val fontSizeSp: Float = 17f,
    val lineSpacing: Float = 1.6f,
    val paragraphSpacing: Float = 10f,
    val marginHorizontal: Int = 28,
    val marginVertical: Int = 28,
    val themePreset: NovelReaderThemePreset = NovelReaderThemePreset.PAPER,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val textDirection: TextDirection = TextDirection.LTR,
    val enableDualPage: Boolean = true,
    val enableFullscreen: Boolean = true,
    val showReadingStatus: Boolean = true,  // 显示阅读状态（之前是 showFooter）
    val isReadingStatusTransparent: Boolean = true,
    val enableParagraphIndent: Boolean = true, // 段首缩进两个全角空格
    val isTranslationEnabled: Boolean = false,
    val translationDisplayMode: NovelTranslationDisplayMode = NovelTranslationDisplayMode.TRANSLATION_ONLY,
) {

    fun save(context: Context) {
        getPrefs(context).edit {
            putFloat(KEY_FONT_SIZE, fontSizeSp)
            putFloat(KEY_LINE_SPACING, lineSpacing)
            putFloat(KEY_PARAGRAPH_SPACING, paragraphSpacing)
            putInt(KEY_MARGIN_HORIZONTAL, marginHorizontal)
            putInt(KEY_MARGIN_VERTICAL, marginVertical)
            putString(KEY_THEME_PRESET, themePreset.name)
            putString(KEY_READING_MODE, readingMode.name)
            putString(KEY_TEXT_DIRECTION, textDirection.name)
            putBoolean(KEY_DUAL_PAGE, enableDualPage)
            putBoolean(KEY_FULLSCREEN, enableFullscreen)
            putBoolean(KEY_SHOW_READING_STATUS, showReadingStatus)
            putBoolean(KEY_READING_STATUS_TRANSPARENT, isReadingStatusTransparent)
            putBoolean(KEY_PARAGRAPH_INDENT, enableParagraphIndent)
            remove(KEY_TRANSLATION_ENABLED)
            putString(KEY_TRANSLATION_DISPLAY_MODE, translationDisplayMode.name)
        }
    }

    companion object {
        private const val PREF_NAME = "novel_reader_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_PARAGRAPH_SPACING = "paragraph_spacing"
        private const val KEY_MARGIN_HORIZONTAL = "margin_horizontal"
        private const val KEY_MARGIN_VERTICAL = "margin_vertical"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_TEXT_DIRECTION = "text_direction"
        private const val KEY_DUAL_PAGE = "dual_page"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_SHOW_READING_STATUS = "show_reading_status"
        private const val KEY_READING_STATUS_TRANSPARENT = "reading_status_transparent"
        private const val KEY_PARAGRAPH_INDENT = "paragraph_indent"
        private const val KEY_TRANSLATION_ENABLED = "translation_enabled"
        private const val KEY_TRANSLATION_DISPLAY_MODE = "translation_display_mode"

        fun load(context: Context): NovelReaderSettings {
            val prefs = getPrefs(context)
            return NovelReaderSettings(
                fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 17f),
                lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.6f),
                paragraphSpacing = prefs.getFloat(KEY_PARAGRAPH_SPACING, 10f),
                marginHorizontal = prefs.getInt(KEY_MARGIN_HORIZONTAL, 28),
                marginVertical = prefs.getInt(KEY_MARGIN_VERTICAL, 28),
                themePreset = runCatching {
                    NovelReaderThemePreset.valueOf(
                        prefs.getString(KEY_THEME_PRESET, null) ?: NovelReaderThemePreset.PAPER.name
                    )
                }.getOrDefault(NovelReaderThemePreset.PAPER),
                readingMode = runCatching {
                    ReadingMode.valueOf(prefs.getString(KEY_READING_MODE, null) ?: ReadingMode.PAGED.name)
                }.getOrDefault(ReadingMode.PAGED),
                textDirection = runCatching {
                    TextDirection.valueOf(prefs.getString(KEY_TEXT_DIRECTION, null) ?: TextDirection.LTR.name)
                }.getOrDefault(TextDirection.LTR),
                enableDualPage = prefs.getBoolean(KEY_DUAL_PAGE, true),
                enableFullscreen = prefs.getBoolean(KEY_FULLSCREEN, true),
                showReadingStatus = prefs.getBoolean(KEY_SHOW_READING_STATUS, true),
                isReadingStatusTransparent = prefs.getBoolean(KEY_READING_STATUS_TRANSPARENT, true),
                enableParagraphIndent = prefs.getBoolean(KEY_PARAGRAPH_INDENT, true),
                isTranslationEnabled = false,
                translationDisplayMode = runCatching {
                    NovelTranslationDisplayMode.valueOf(
                        prefs.getString(KEY_TRANSLATION_DISPLAY_MODE, null)
                            ?: NovelTranslationDisplayMode.TRANSLATION_ONLY.name
                    )
                }.getOrDefault(NovelTranslationDisplayMode.TRANSLATION_ONLY),
            )
        }

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }
}

enum class NovelReaderThemePreset {
    PAPER,
    SEPIA,
    MOSS,
    SLATE,
}

data class NovelReaderPalette(
    val backgroundColor: Int,
    val textColor: Int,
    val secondaryTextColor: Int,
    val chromeBackgroundColor: Int,
    val chromeTextColor: Int,
    val highlightColor: Int,
    val placeholderColor: Int,
    val placeholderTextColor: Int,
    val isDark: Boolean,
)

fun novelReaderPalette(
    preset: NovelReaderThemePreset,
    isDarkTheme: Boolean,
): NovelReaderPalette {
    return when (preset) {
        NovelReaderThemePreset.PAPER -> if (isDarkTheme) {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#221F1B"),
                textColor = Color.parseColor("#DED6C9"),
                secondaryTextColor = Color.parseColor("#B7AEA0"),
                chromeBackgroundColor = Color.parseColor("#2D2924"),
                chromeTextColor = Color.parseColor("#E6DED1"),
                highlightColor = Color.parseColor("#4DB68A4A"),
                placeholderColor = Color.parseColor("#3B352F"),
                placeholderTextColor = Color.parseColor("#B7AEA0"),
                isDark = true,
            )
        } else {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#F4ECD8"),
                textColor = Color.parseColor("#4F4032"),
                secondaryTextColor = Color.parseColor("#7A6A59"),
                chromeBackgroundColor = Color.parseColor("#E7DDC5"),
                chromeTextColor = Color.parseColor("#544436"),
                highlightColor = Color.parseColor("#4DA67C2E"),
                placeholderColor = Color.parseColor("#DDD2BC"),
                placeholderTextColor = Color.parseColor("#7A6A59"),
                isDark = false,
            )
        }

        NovelReaderThemePreset.SEPIA -> if (isDarkTheme) {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#2A241D"),
                textColor = Color.parseColor("#E4D4B8"),
                secondaryTextColor = Color.parseColor("#B9A58A"),
                chromeBackgroundColor = Color.parseColor("#342D25"),
                chromeTextColor = Color.parseColor("#EFDFC2"),
                highlightColor = Color.parseColor("#4DBA8748"),
                placeholderColor = Color.parseColor("#43382D"),
                placeholderTextColor = Color.parseColor("#B9A58A"),
                isDark = true,
            )
        } else {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#F1E4CC"),
                textColor = Color.parseColor("#5A4330"),
                secondaryTextColor = Color.parseColor("#8A7057"),
                chromeBackgroundColor = Color.parseColor("#E4D3B7"),
                chromeTextColor = Color.parseColor("#614734"),
                highlightColor = Color.parseColor("#4DA87B2C"),
                placeholderColor = Color.parseColor("#DCC7A6"),
                placeholderTextColor = Color.parseColor("#8A7057"),
                isDark = false,
            )
        }

        NovelReaderThemePreset.MOSS -> if (isDarkTheme) {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#1F2520"),
                textColor = Color.parseColor("#D5DDD2"),
                secondaryTextColor = Color.parseColor("#A5B19E"),
                chromeBackgroundColor = Color.parseColor("#293029"),
                chromeTextColor = Color.parseColor("#E0E7DB"),
                highlightColor = Color.parseColor("#4D7DA062"),
                placeholderColor = Color.parseColor("#333B34"),
                placeholderTextColor = Color.parseColor("#A5B19E"),
                isDark = true,
            )
        } else {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#E6E9DB"),
                textColor = Color.parseColor("#36402E"),
                secondaryTextColor = Color.parseColor("#61705A"),
                chromeBackgroundColor = Color.parseColor("#D6DBC8"),
                chromeTextColor = Color.parseColor("#3D4735"),
                highlightColor = Color.parseColor("#4D6F8A42"),
                placeholderColor = Color.parseColor("#C8D0BA"),
                placeholderTextColor = Color.parseColor("#61705A"),
                isDark = false,
            )
        }

        NovelReaderThemePreset.SLATE -> if (isDarkTheme) {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#1D2329"),
                textColor = Color.parseColor("#D7DDE2"),
                secondaryTextColor = Color.parseColor("#AEB8C0"),
                chromeBackgroundColor = Color.parseColor("#252D34"),
                chromeTextColor = Color.parseColor("#E3E8EC"),
                highlightColor = Color.parseColor("#4D5F8EAD"),
                placeholderColor = Color.parseColor("#313A42"),
                placeholderTextColor = Color.parseColor("#AEB8C0"),
                isDark = true,
            )
        } else {
            NovelReaderPalette(
                backgroundColor = Color.parseColor("#E8EDF1"),
                textColor = Color.parseColor("#3A4650"),
                secondaryTextColor = Color.parseColor("#66737D"),
                chromeBackgroundColor = Color.parseColor("#DCE4EA"),
                chromeTextColor = Color.parseColor("#404B54"),
                highlightColor = Color.parseColor("#4D6D93B0"),
                placeholderColor = Color.parseColor("#CED8DF"),
                placeholderTextColor = Color.parseColor("#66737D"),
                isDark = false,
            )
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
