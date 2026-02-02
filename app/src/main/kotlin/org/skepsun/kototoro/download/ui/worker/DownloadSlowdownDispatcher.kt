package org.skepsun.kototoro.download.ui.worker

import android.os.SystemClock
import androidx.collection.MutableObjectLongMap
import kotlinx.coroutines.delay
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.ParserMangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSlowdownDispatcher @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
) {
	private val timeMap = MutableObjectLongMap<MangaSource>()

	suspend fun delay(source: MangaSource) {
		val repo = mangaRepositoryFactory.create(source)
		if (!repo.isSlowdownEnabled()) {
			return
		}
		val delayMs = if (settings.isDownloadAlignedWithReader) {
			0L
		} else {
			settings.downloadRequestDelayMs.toLong()
		}
		if (delayMs <= 0L) {
			return
		}
		val lastRequest = synchronized(timeMap) {
			val res = timeMap.getOrDefault(source, 0L)
			timeMap[source] = SystemClock.elapsedRealtime()
			res
		}
		if (lastRequest != 0L) {
			val waitMs = lastRequest + delayMs - SystemClock.elapsedRealtime()
			if (waitMs > 0L) {
				delay(waitMs)
			}
		}
	}
}
