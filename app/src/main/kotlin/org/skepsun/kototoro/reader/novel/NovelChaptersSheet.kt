package org.skepsun.kototoro.reader.novel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.skepsun.kototoro.databinding.ItemNovelChapterBinding
import org.skepsun.kototoro.databinding.SheetNovelChaptersBinding
import org.skepsun.kototoro.parsers.model.MangaChapter

/**
 * 小说章节选择器
 */
class NovelChaptersSheet : BottomSheetDialogFragment() {

    private var _binding: SheetNovelChaptersBinding? = null
    private val binding get() = _binding!!

    private var chapters: List<MangaChapter> = emptyList()
    private var currentIndex: Int = 0
    private var isReversed: Boolean = false
    private var callback: Callback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetNovelChaptersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取回调
        callback = parentFragment as? Callback ?: activity as? Callback

        // 设置章节数量
        binding.textChapterCount.text = "共 ${chapters.size} 章"

        // 设置 RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        updateAdapter()

        // 反转按钮
        binding.buttonReverse.setOnClickListener {
            isReversed = !isReversed
            updateAdapter()
        }

        // 滚动到当前章节（考虑分组标题）
        binding.recyclerView.post {
            scrollToCurrentChapter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateAdapter() {
        val displayChapters = if (isReversed) {
            chapters.reversed()
        } else {
            chapters
        }

        binding.recyclerView.adapter = ChaptersAdapter(
            displayChapters,
            if (isReversed) chapters.size - 1 - currentIndex else currentIndex
        ) { position ->
            val actualIndex = if (isReversed) {
                chapters.size - 1 - position
            } else {
                position
            }
            callback?.onChapterSelected(actualIndex)
            dismiss()
        }
    }

    private fun scrollToCurrentChapter() {
        val adapter = binding.recyclerView.adapter as? ChaptersAdapter ?: return
        val targetChapterIndex = if (isReversed) {
            chapters.size - 1 - currentIndex
        } else {
            currentIndex
        }
        
        // 找到对应章节在 items 列表中的位置
        val position = adapter.findChapterPosition(targetChapterIndex)
        if (position >= 0) {
            // 使用 smoothScrollToPosition 以获得更好的视觉效果
            binding.recyclerView.smoothScrollToPosition(position)
        }
    }

    fun setChapters(chapters: List<MangaChapter>, currentIndex: Int) {
        this.chapters = chapters
        this.currentIndex = currentIndex
    }

    interface Callback {
        fun onChapterSelected(index: Int)
    }

    companion object {
        fun newInstance(
            chapters: List<MangaChapter>,
            currentIndex: Int
        ): NovelChaptersSheet {
            return NovelChaptersSheet().apply {
                setChapters(chapters, currentIndex)
            }
        }
    }

    /**
     * 章节适配器 - 支持分组显示
     */
    private class ChaptersAdapter(
        private val chapters: List<MangaChapter>,
        private val currentIndex: Int,
        private val onChapterClick: (Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_CHAPTER = 1
        }

        private val items: List<Item>

        init {
            items = buildItemList()
        }

        private fun buildItemList(): List<Item> {
            val result = mutableListOf<Item>()
            var currentBranch: String? = null

            chapters.forEachIndexed { index, chapter ->
                val branch = chapter.branch
                
                // 如果分组变化，添加分组标题
                if (branch != currentBranch && !branch.isNullOrBlank()) {
                    result.add(Item.Header(branch))
                    currentBranch = branch
                }
                
                result.add(Item.Chapter(chapter, index))
            }
            
            return result
        }

        sealed class Item {
            data class Header(val title: String) : Item()
            data class Chapter(val chapter: MangaChapter, val originalIndex: Int) : Item()
        }

        class HeaderViewHolder(val binding: org.skepsun.kototoro.databinding.ItemNovelChapterHeaderBinding) : 
            RecyclerView.ViewHolder(binding.root)
        
        class ChapterViewHolder(val binding: ItemNovelChapterBinding) : 
            RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is Item.Header -> TYPE_HEADER
                is Item.Chapter -> TYPE_CHAPTER
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER -> {
                    val binding = org.skepsun.kototoro.databinding.ItemNovelChapterHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    HeaderViewHolder(binding)
                }
                TYPE_CHAPTER -> {
                    val binding = ItemNovelChapterBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    ChapterViewHolder(binding)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> {
                    val headerHolder = holder as HeaderViewHolder
                    headerHolder.binding.textTitle.text = item.title
                }
                is Item.Chapter -> {
                    val chapterHolder = holder as ChapterViewHolder
                    val chapter = item.chapter
                    val isCurrent = item.originalIndex == currentIndex

                    chapterHolder.binding.textTitle.text = chapter.title ?: "未命名章节"
                    chapterHolder.binding.indicator.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE

                    // 当前章节高亮
                    chapterHolder.binding.root.alpha = if (isCurrent) 1.0f else 0.7f

                    chapterHolder.binding.root.setOnClickListener {
                        onChapterClick(item.originalIndex)
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        /**
         * 查找指定章节索引在 items 列表中的位置
         */
        fun findChapterPosition(chapterIndex: Int): Int {
            return items.indexOfFirst { item ->
                item is Item.Chapter && item.originalIndex == chapterIndex
            }
        }
    }
}
