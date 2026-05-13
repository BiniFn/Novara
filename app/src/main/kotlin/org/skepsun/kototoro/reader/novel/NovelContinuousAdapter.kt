package org.skepsun.kototoro.reader.novel

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import java.io.File

data class NovelChapterData(
    val chapterIndex: Int, // The absolute index of the chapter in the TOC
    val content: String,
    val epubFile: File?,
    val chapterPath: String?,
    val translation: NovelChapterTranslation? = null,
)

class NovelContinuousAdapter(
    private var settings: NovelReaderSettings,
    private val onImageClick: (NovelInlineImageRequest) -> Unit,
    private val onTap: (Float, Float, Long) -> Unit,
) : RecyclerView.Adapter<NovelContinuousAdapter.ChapterViewHolder>() {

    private val chapters = mutableListOf<NovelChapterData>()
    private var palette: NovelReaderPalette? = null

    class ChapterViewHolder(val view: NovelChapterView) : RecyclerView.ViewHolder(view) {
        fun bind(
            data: NovelChapterData,
            settings: NovelReaderSettings,
            palette: NovelReaderPalette?,
            onImageClick: (NovelInlineImageRequest) -> Unit,
            onTap: (Float, Float, Long) -> Unit,
        ) {
            view.updateSettings(settings)
            palette?.let(view::updatePalette)
            view.onImageClickListener = onImageClick
            view.onTapListener = onTap
            view.setContent(data.content, data.epubFile, data.chapterPath)
            view.setTranslation(data.translation)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = NovelChapterView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapters[position], settings, palette, onImageClick, onTap)
    }

    override fun getItemCount(): Int = chapters.size

    fun updateSettings(newSettings: NovelReaderSettings) {
        settings = newSettings
        notifyDataSetChanged()
    }

    fun updatePalette(newPalette: NovelReaderPalette) {
        palette = newPalette
        notifyDataSetChanged()
    }
    
    fun getItems(): List<NovelChapterData> = chapters.toList()

    fun setInitialChapter(data: NovelChapterData) {
        chapters.clear()
        chapters.add(data)
        notifyDataSetChanged()
    }

    fun prependChapter(data: NovelChapterData) {
        // Prevent duplicates
        if (chapters.isNotEmpty() && chapters.first().chapterIndex == data.chapterIndex) return
        chapters.add(0, data)
        notifyItemInserted(0)
    }

    fun appendChapter(data: NovelChapterData) {
        // Prevent duplicates
        if (chapters.isNotEmpty() && chapters.last().chapterIndex == data.chapterIndex) return
        chapters.add(data)
        notifyItemInserted(chapters.size - 1)
    }
    
    fun clear() {
        val size = chapters.size
        chapters.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * 更新指定章节的翻译结果，触发对应 item 的局部刷新。
     */
    fun updateTranslation(chapterIndex: Int, translation: NovelChapterTranslation) {
        val position = chapters.indexOfFirst { it.chapterIndex == chapterIndex }
        if (position < 0) return
        chapters[position] = chapters[position].copy(translation = translation)
        notifyItemChanged(position)
    }

    /**
     * 清除所有章节的翻译，恢复原文显示。
     */
    fun clearTranslations() {
        for (i in chapters.indices) {
            chapters[i] = chapters[i].copy(translation = null)
        }
        notifyDataSetChanged()
    }
}
