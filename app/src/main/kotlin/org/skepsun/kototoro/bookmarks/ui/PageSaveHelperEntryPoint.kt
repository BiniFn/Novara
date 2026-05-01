package org.skepsun.kototoro.bookmarks.ui

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import org.skepsun.kototoro.reader.ui.PageSaveHelper

@EntryPoint
@InstallIn(ActivityComponent::class)
interface PageSaveHelperEntryPoint {
    fun pageSaveHelperFactory(): PageSaveHelper.Factory
}
