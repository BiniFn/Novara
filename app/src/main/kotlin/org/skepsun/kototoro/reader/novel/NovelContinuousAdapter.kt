package org.skepsun.kototoro.reader.novel

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import java.io.File

data class NovelChapterData(
    val chapterIndex: Int, // The absolute index of the chapter in the TOC
    val content: String,
    val epubFile: File?,
    val chapterPath: String?
)

class NovelContinuousAdapter(
    private var settings: NovelReaderSettings
) : RecyclerView.Adapter<NovelContinuousAdapter.ChapterViewHolder>() {

    private val chapters = mutableListOf<NovelChapterData>()

    class ChapterViewHolder(val view: NovelChapterView) : RecyclerView.ViewHolder(view) {
        fun bind(data: NovelChapterData, settings: NovelReaderSettings) {
            view.updateSettings(settings)
            view.setContent(data.content, data.epubFile, data.chapterPath)
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
        holder.bind(chapters[position], settings)
    }

    override fun getItemCount(): Int = chapters.size

    fun updateSettings(newSettings: NovelReaderSettings) {
        settings = newSettings
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
}
