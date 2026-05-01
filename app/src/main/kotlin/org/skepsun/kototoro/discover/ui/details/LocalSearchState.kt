package org.skepsun.kototoro.discover.ui.details

import org.skepsun.kototoro.parsers.model.Content

sealed class LocalSearchState {
    object Loading : LocalSearchState()
    data class Loaded(val items: List<Content>) : LocalSearchState()
    data class Error(val throwable: Throwable) : LocalSearchState()
}
