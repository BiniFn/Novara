package org.skepsun.kototoro.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.SearchRecentSuggestions
import android.text.Html
import androidx.collection.arraySetOf
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.backups.domain.BackupObserver
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.exceptions.resolve.CaptchaHandler
import org.skepsun.kototoro.core.image.AvifImageDecoder
import org.skepsun.kototoro.core.image.CbzFetcher
import org.skepsun.kototoro.core.image.MangaSourceHeaderInterceptor
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.network.imageproxy.ImageProxyInterceptor
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.core.parser.MangaLoaderContextImpl
import org.skepsun.kototoro.core.parser.favicon.FaviconFetcher
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.image.CoilImageGetter
import org.skepsun.kototoro.core.ui.util.ActivityRecreationHandle
import org.skepsun.kototoro.core.util.AcraScreenLogger
import org.skepsun.kototoro.core.util.FileSize
import org.skepsun.kototoro.core.util.ext.connectivityManager
import org.skepsun.kototoro.core.util.ext.isLowRamDevice
import org.skepsun.kototoro.details.ui.pager.pages.MangaPageFetcher
import org.skepsun.kototoro.details.ui.pager.pages.MangaPageKeyer
import org.skepsun.kototoro.local.data.CacheDir
import org.skepsun.kototoro.local.data.FaviconCache
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.data.NovelCache
import org.skepsun.kototoro.local.data.PageCache
import org.skepsun.kototoro.local.domain.model.LocalManga
import org.skepsun.kototoro.main.domain.CoverRestoreInterceptor
import org.skepsun.kototoro.main.ui.protect.AppProtectHelper
import org.skepsun.kototoro.main.ui.protect.ScreenshotPolicyHelper
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.search.ui.MangaSuggestionsProvider
import org.skepsun.kototoro.sync.domain.SyncController
import org.skepsun.kototoro.widget.WidgetUpdater
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {

	@Binds
	fun bindMangaLoaderContext(mangaLoaderContextImpl: MangaLoaderContextImpl): MangaLoaderContext

	@Binds
	fun bindImageGetter(coilImageGetter: CoilImageGetter): Html.ImageGetter

	companion object {

		@Provides
		@LocalizedAppContext
		fun provideLocalizedContext(
			@ApplicationContext context: Context,
		): Context = ContextCompat.getContextForLanguage(context)

		@Provides
		@Singleton
		fun provideNetworkState(
			@ApplicationContext context: Context,
			settings: AppSettings,
		) = NetworkState(context.connectivityManager, settings)

		@Provides
		@Singleton
		fun provideMangaDatabase(
			@ApplicationContext context: Context,
		): MangaDatabase = MangaDatabase(context)

		@Provides
		@Singleton
		fun provideJsonSourceDao(database: MangaDatabase): org.skepsun.kototoro.core.db.dao.JsonSourceDao {
			return database.getJsonSourceDao()
		}

		@Provides
		@Singleton
		fun provideExternalExtensionRepoDao(database: MangaDatabase): org.skepsun.kototoro.core.db.dao.ExternalExtensionRepoDao {
			return database.getExternalExtensionRepoDao()
		}

		@Provides
		@Singleton
		fun provideJson(): kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
			ignoreUnknownKeys = true
			isLenient = true
			encodeDefaults = true
			prettyPrint = true
			coerceInputValues = true
		}

		@Provides
		@Singleton
		fun provideCoil(
			@LocalizedAppContext context: Context,
			@MangaHttpClient okHttpClientProvider: Provider<OkHttpClient>,
			faviconFetcherFactory: FaviconFetcher.Factory,
			imageProxyInterceptor: ImageProxyInterceptor,
			pageFetcherFactory: MangaPageFetcher.Factory,
			coverRestoreInterceptor: CoverRestoreInterceptor,
			networkStateProvider: Provider<NetworkState>,
			captchaHandler: CaptchaHandler,
		): ImageLoader {
			val diskCacheFactory = {
				val rootDir = context.externalCacheDir ?: context.cacheDir
				DiskCache.Builder()
					.directory(rootDir.resolve(CacheDir.THUMBS.dir))
					.build()
			}
			val okHttpClientLazy = lazy {
				okHttpClientProvider.get().newBuilder().cache(null).build()
			}
			return ImageLoader.Builder(context)
				.interceptorCoroutineContext(Dispatchers.Default)
				.diskCache(diskCacheFactory)
				.logger(if (BuildConfig.DEBUG) DebugLogger() else null)
				.allowRgb565(context.isLowRamDevice())
				.eventListener(captchaHandler)
				.components {
					add(
						OkHttpNetworkFetcherFactory(
							callFactory = okHttpClientLazy::value,
							connectivityChecker = { networkStateProvider.get() },
						),
					)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						add(AnimatedImageDecoder.Factory())
					} else {
						add(GifDecoder.Factory())
					}
					add(SvgDecoder.Factory())
					add(CbzFetcher.Factory())
					add(AvifImageDecoder.Factory())
					add(faviconFetcherFactory)
					add(MangaPageKeyer())
					add(pageFetcherFactory)
					add(imageProxyInterceptor)
					add(coverRestoreInterceptor)
					add(MangaSourceHeaderInterceptor())
				}.build()
		}

		@Provides
		fun provideSearchSuggestions(
			@ApplicationContext context: Context,
		): SearchRecentSuggestions = MangaSuggestionsProvider.createSuggestions(context)

		@Provides
		@ElementsIntoSet
		fun provideDatabaseObservers(
			widgetUpdater: WidgetUpdater,
			appShortcutManager: AppShortcutManager,
			backupObserver: BackupObserver,
			syncController: SyncController,
		): Set<@JvmSuppressWildcards InvalidationTracker.Observer> = arraySetOf(
			widgetUpdater,
			appShortcutManager,
			backupObserver,
			syncController,
		)

		@Provides
		@ElementsIntoSet
		fun provideActivityLifecycleCallbacks(
			appProtectHelper: AppProtectHelper,
			activityRecreationHandle: ActivityRecreationHandle,
			acraScreenLogger: AcraScreenLogger,
			screenshotPolicyHelper: ScreenshotPolicyHelper,
		): Set<@JvmSuppressWildcards Application.ActivityLifecycleCallbacks> = arraySetOf(
			appProtectHelper,
			activityRecreationHandle,
			acraScreenLogger,
			screenshotPolicyHelper,
		)

		@Provides
		@Singleton
		@LocalStorageChanges
		fun provideMutableLocalStorageChangesFlow(): MutableSharedFlow<LocalManga?> = MutableSharedFlow()

		@Provides
		@LocalStorageChanges
		fun provideLocalStorageChangesFlow(
			@LocalStorageChanges flow: MutableSharedFlow<LocalManga?>,
		): SharedFlow<LocalManga?> = flow.asSharedFlow()

		@Provides
		fun provideWorkManager(
			@ApplicationContext context: Context,
		): WorkManager = WorkManager.getInstance(context)

		@Provides
		@Singleton
		@PageCache
		fun providePageCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.PAGES,
			defaultSize = FileSize.MEGABYTES.convert(200, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(20, FileSize.BYTES),
		)

		@Provides
		@Singleton
		@FaviconCache
		fun provideFaviconCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.FAVICONS,
			defaultSize = FileSize.MEGABYTES.convert(8, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(2, FileSize.BYTES),
		)

		@Provides
		@Singleton
		@NovelCache
		fun provideNovelCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.NOVELS,
			defaultSize = FileSize.MEGABYTES.convert(100, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(10, FileSize.BYTES),
		)
	}
}
