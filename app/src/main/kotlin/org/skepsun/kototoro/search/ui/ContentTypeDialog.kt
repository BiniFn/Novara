package org.skepsun.kototoro.search.ui

import android.content.Context
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.SEARCH_CONTENT_KIND_OPTIONS
import org.skepsun.kototoro.search.domain.SearchContentKind

fun showContentTypeDialog(
	context: Context,
	current: Set<SearchContentKind>,
	onApply: (Set<SearchContentKind>) -> Unit,
) {
	val options = SEARCH_CONTENT_KIND_OPTIONS
	val labels = options.map { context.getString(it.titleRes) }.toTypedArray()
	val selected = current.toMutableSet()
	val checked = BooleanArray(options.size) { index ->
		options[index].kind in current
	}
	buildAlertDialog(context) {
		setTitle(R.string.type)
		setMultiChoiceItems(labels, checked) { _, which, isChecked ->
			val kind = options[which].kind
			if (isChecked) {
				selected.add(kind)
			} else {
				selected.remove(kind)
			}
		}
		setNegativeButton(android.R.string.cancel, null)
		setPositiveButton(android.R.string.ok) { _, _ ->
			val resolved = if (selected.isEmpty()) ALL_SEARCH_CONTENT_KINDS else selected
			onApply(resolved)
		}
	}.show()
}
