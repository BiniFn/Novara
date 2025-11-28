package org.skepsun.kototoro.reader.novel

import android.text.StaticLayout

/**
 * 小说页面数据
 * 类似于漫画阅读器的 ReaderPage
 */
data class NovelPage(
    val chapterId: Long,           // 所属章节 ID
    val chapterIndex: Int,         // 章节在列表中的索引
    val pageIndex: Int,            // 页面在章节中的索引
    val text: String,              // 页面文本内容
    val layout: StaticLayout?,     // 文本布局（用于绘制）
    val charStartPosition: Int,    // 在章节中的起始字符位置
    val charEndPosition: Int,      // 在章节中的结束字符位置
) {
    /**
     * 全局页面索引（在所有章节中的位置）
     * 由 NovelChapterPages 管理
     */
    var globalIndex: Int = 0
        internal set
}
