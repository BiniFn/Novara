package org.skepsun.kototoro.list.ui.adapter

import android.view.View
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.list.ui.model.MangaListModel
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaTag

interface MangaDetailsClickListener : OnListItemClickListener<MangaListModel> {

	fun onReadClick(manga: Manga, view: View)

	fun onTagClick(manga: Manga, tag: MangaTag, view: View)
}
