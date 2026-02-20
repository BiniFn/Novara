package org.skepsun.kototoro.search.ui.suggestion

import android.text.Editable
import android.view.KeyEvent
import android.widget.TextView
import androidx.core.net.toUri
import com.google.android.material.search.SearchView
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.MangaLinkResolver
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.search.domain.SearchKind

class SearchSuggestionListenerImpl(
	private val router: AppRouter,
	private val searchView: SearchView,
	private val viewModel: SearchSuggestionViewModel,
) : SearchSuggestionListener {

	override fun onMangaClick(manga: Manga) {
		router.openDetails(manga)
	}

	override fun onQueryClick(query: String, kind: SearchKind, submit: Boolean) {
		if (submit && query.isNotEmpty()) {
			if (kind == SearchKind.SIMPLE && MangaLinkResolver.isValidLink(query)) {
				router.openDetails(query.toUri())
			} else {
				router.openSearch(
					query = query,
					kind = kind,
					sourceTypes = viewModel.getSourceTypes(),
					contentKinds = viewModel.getContentKinds(),
				)
				if (kind != SearchKind.TAG) {
					viewModel.saveQuery(query)
				}
			}
			searchView.hide()
		} else {
			searchView.setText(query)
		}
	}

	override fun onTagClick(tag: MangaTag) {
		router.openSearch(
			query = tag.title,
			kind = SearchKind.TAG,
			sourceTypes = viewModel.getSourceTypes(),
			contentKinds = viewModel.getContentKinds(),
		)
	}

	override fun onSourceToggle(source: MangaSource, isEnabled: Boolean) {
		viewModel.onSourceToggle(source, isEnabled)
	}

	override fun onSourceClick(source: MangaSource) {
		router.openList(source, null, null)
	}

	override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

	override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

	override fun afterTextChanged(s: Editable?) {
		viewModel.onQueryChanged(s?.toString().orEmpty())
	}

	override fun onEditorAction(
		v: TextView?,
		actionId: Int,
		event: KeyEvent?
	): Boolean {
		val query = v?.text?.toString()
		if (query.isNullOrEmpty()) {
			return false
		}
		onQueryClick(query, SearchKind.SIMPLE, true)
		return true
	}
}
