package org.skepsun.kototoro.core.nav

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.parser.ContentRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppRouterEntryPoint {

    val settings: AppSettings
    val mangaRepositoryFactory: ContentRepository.Factory
    val jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager
}
