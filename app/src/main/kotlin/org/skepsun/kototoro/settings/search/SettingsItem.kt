package org.skepsun.kototoro.settings.search

import androidx.fragment.app.Fragment
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.settings.SettingsDestination

data class SettingsItem(
	val key: String,
	val title: CharSequence,
	val breadcrumbs: List<String>,
	val destination: SettingsDestination,
) : ListModel {

	constructor(
		key: String,
		title: CharSequence,
		breadcrumbs: List<String>,
		fragmentClass: Class<out Fragment>,
	) : this(
		key = key,
		title = title,
		breadcrumbs = breadcrumbs,
		destination = SettingsDestination.FragmentDestination(fragmentClass),
	)

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is SettingsItem && other.key == key && other.destination == destination
	}
}
