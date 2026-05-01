package org.skepsun.kototoro.main.ui.compose

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.ui.PageSaveHelper

@EntryPoint
@InstallIn(ActivityComponent::class)
interface DetailsRouteEntryPoint {
    fun settings(): AppSettings
    fun pageSaveHelperFactory(): PageSaveHelper.Factory
    fun appShortcutManager(): AppShortcutManager
}
