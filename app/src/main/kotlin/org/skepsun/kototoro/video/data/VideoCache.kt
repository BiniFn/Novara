package org.skepsun.kototoro.video.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频缓存管理器
 * 提供ExoPlayer的缓存支持，实现：
 * 1. 视频片段缓存，避免重复下载
 * 2. 断点续播，退出后可继续播放
 * 3. 自动清理旧缓存（LRU策略）
 */
@Singleton
class VideoCache @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	companion object {
		// 缓存大小限制：512MB
		private const val CACHE_SIZE = 512L * 1024 * 1024
	}

	val cache: Cache by lazy {
		val cacheDir = File(context.externalCacheDir ?: context.cacheDir, "video_cache")
		val databaseProvider = StandaloneDatabaseProvider(context)
		val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
		
		SimpleCache(cacheDir, evictor, databaseProvider)
	}

	fun clear() {
		runCatching {
			cache.keys.forEach { key ->
				cache.removeResource(key)
			}
		}
	}

	fun getCacheSize(): Long {
		return runCatching {
			cache.cacheSpace
		}.getOrDefault(0L)
	}
}
