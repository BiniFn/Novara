package org.skepsun.kototoro.bookmarks.ui.adapter

import android.util.Base64
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.jsoup.Jsoup
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.databinding.ItemBookmarkNovelBinding
import org.skepsun.kototoro.list.ui.model.ListModel

fun bookmarkNovelAD(
	clickListener: OnListItemClickListener<Bookmark>,
) = adapterDelegateViewBinding<Bookmark, ListModel, ItemBookmarkNovelBinding>(
	{ inflater, parent -> ItemBookmarkNovelBinding.inflate(inflater, parent, false) },
) {
	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		// 从imageUrl中提取文本预览
		val previewText = extractTextPreview(item.imageUrl)
		binding.textViewPreview.text = previewText
		binding.progressView.setProgress(item.percent, false)
	}
}

/**
 * 从data URL或HTML中提取文本预览
 */
private fun extractTextPreview(imageUrl: String): String {
	return try {
		when {
			// 处理data URL格式 (data:text/html;charset=utf-8;base64,...)
			imageUrl.startsWith("data:text/html") -> {
				val base64Data = imageUrl.substringAfter("base64,", "")
				if (base64Data.isNotEmpty()) {
					val htmlBytes = Base64.decode(base64Data, Base64.DEFAULT)
					val html = String(htmlBytes, Charsets.UTF_8)
					extractTextFromHtml(html)
				} else {
					"无法加载书签预览"
				}
			}
			// 如果是普通HTML
			imageUrl.contains("<html>") || imageUrl.contains("<!DOCTYPE") -> {
				extractTextFromHtml(imageUrl)
			}
			// 如果是空字符串
			imageUrl.isEmpty() -> "书签位置"
			// 其他情况，直接作为纯文本显示
			else -> imageUrl.take(200).trim()
		}
	} catch (e: Exception) {
		"书签位置"
	}
}

/**
 * 从HTML中提取纯文本
 */
private fun extractTextFromHtml(html: String): String {
	return try {
		val doc = Jsoup.parse(html)
		// 移除script和style标签
		doc.select("script, style").remove()
		// 获取body中的文本
		val text = doc.body()?.text()?.trim() ?: ""
		// 限制长度并清理空白
		text.take(200).replace(Regex("\\s+"), " ")
	} catch (e: Exception) {
		"书签位置"
	}
}
