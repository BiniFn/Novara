package org.skepsun.kototoro.readingrecord.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.reader.ui.ReaderState
import javax.inject.Inject

data class ReadingRecordSummary(
	val totalDuration: Long = 0L,
	val readingDays: Int = 0,
	val lastReadAt: Long? = null,
)

data class ReadingRecordSnapshot(
	val summary: ReadingRecordSummary = ReadingRecordSummary(),
	val sessions: List<ReadingRecordEntity> = emptyList(),
	val chapters: List<ReadingChapterAggregateEntity> = emptyList(),
	val jumpPoints: List<ReadingJumpPointEntity> = emptyList(),
)

class ReadingRecordRepository @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val mangaRepository: ContentDataRepository,
) {

	fun observeSnapshot(mangaId: Long): Flow<ReadingRecordSnapshot> {
		val dao = db.getReadingRecordDao()
		val summaryFlow = combine(
			dao.observeTotalDuration(mangaId),
			dao.observeReadingDays(mangaId),
			dao.observeLastReadAt(mangaId),
		) { totalDuration, readingDays, lastReadAt ->
			ReadingRecordSummary(
				totalDuration = totalDuration,
				readingDays = readingDays,
				lastReadAt = lastReadAt,
			)
		}
		return combine(
			summaryFlow,
			dao.observeSessions(mangaId),
			dao.observeChapterAggregates(mangaId),
			dao.observeJumpPoints(mangaId, DEFAULT_JUMP_LIMIT),
		) { summary, sessions, chapters, jumpPoints ->
			val effectiveSummary = summary.copy(
				totalDuration = summary.totalDuration.takeIf { it > 0L }
					?: sessions.sumOf { (it.endAt - it.startAt).coerceAtLeast(0L) },
				readingDays = summary.readingDays.takeIf { it > 0 }
					?: sessions.map { it.startAt / MILLIS_PER_DAY }.distinct().size,
				lastReadAt = summary.lastReadAt ?: sessions.maxOfOrNull { it.endAt },
			)
			ReadingRecordSnapshot(
				summary = effectiveSummary,
				sessions = sessions,
				chapters = chapters,
				jumpPoints = jumpPoints,
			)
		}
	}

	suspend fun recordSession(
		manga: Content,
		startAt: Long,
		endAt: Long,
		startState: ReaderState,
		startPercent: Float,
		endState: ReaderState,
		endPercent: Float,
		force: Boolean = false,
		allowShort: Boolean = false,
	) {
		if (!force && shouldSkip(manga)) return
		if (!allowShort && endAt - startAt < MIN_SESSION_DURATION_MS) return
		db.withTransaction {
			mangaRepository.storeContent(manga, replaceExisting = true)
			db.getReadingRecordDao().insertSession(
				ReadingRecordEntity(
					mangaId = manga.id,
					startAt = startAt,
					endAt = endAt,
					startChapterId = startState.chapterId,
					startPage = startState.page,
					startScroll = startState.scroll,
					endChapterId = endState.chapterId,
					endPage = endState.page,
					endScroll = endState.scroll,
					startPercent = startPercent,
					endPercent = endPercent,
				),
			)
		}
	}

	suspend fun recordJumpPoint(
		manga: Content,
		fromState: ReaderState,
		fromPercent: Float,
		toState: ReaderState,
		toPercent: Float,
		source: String,
		force: Boolean = false,
	) {
		if (!force && shouldSkip(manga)) return
		db.withTransaction {
			mangaRepository.storeContent(manga, replaceExisting = true)
			db.getReadingRecordDao().insertJumpPoint(
				ReadingJumpPointEntity(
					mangaId = manga.id,
					createdAt = System.currentTimeMillis(),
					fromChapterId = fromState.chapterId,
					fromPage = fromState.page,
					fromScroll = fromState.scroll,
					fromPercent = fromPercent,
					toChapterId = toState.chapterId,
					toPage = toState.page,
					toScroll = toState.scroll,
					toPercent = toPercent,
					source = source,
				),
			)
		}
	}

	suspend fun clearForContent(mangaId: Long) = db.withTransaction {
		val dao = db.getReadingRecordDao()
		dao.clearSessions(mangaId)
		dao.clearJumpPoints(mangaId)
	}

	suspend fun deleteSession(id: Long) {
		db.getReadingRecordDao().deleteSession(id)
	}

	suspend fun deleteJumpPoint(id: Long) {
		db.getReadingRecordDao().deleteJumpPoint(id)
	}

	fun shouldSkip(manga: Content): Boolean = settings.isIncognitoModeEnabled(manga.isNsfw())

	private companion object {
		const val DEFAULT_JUMP_LIMIT = 20
		const val MIN_SESSION_DURATION_MS = 5_000L
		const val MILLIS_PER_DAY = 86_400_000L
	}
}
