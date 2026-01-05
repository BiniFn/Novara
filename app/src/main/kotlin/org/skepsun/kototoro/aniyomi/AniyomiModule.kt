package org.skepsun.kototoro.aniyomi

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.skepsun.kototoro.aniyomi.compat.KotoAniyomiInjektBridge
import org.skepsun.kototoro.mihon.compat.KotoInjektBridge
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AniyomiModule {

    @Provides
    @Singleton
    fun provideKotoAniyomiInjektBridge(
        mihonInjektBridge: KotoInjektBridge
    ): KotoAniyomiInjektBridge {
        return KotoAniyomiInjektBridge(mihonInjektBridge)
    }
}
