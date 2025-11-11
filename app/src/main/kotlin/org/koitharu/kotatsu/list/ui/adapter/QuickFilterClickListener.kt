package org.skepsun.kototoro.list.ui.adapter

import org.skepsun.kototoro.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
