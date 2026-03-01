package org.skepsun.kototoro.scrobbling.mangaupdates.domain

import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.scrobbling.mangaupdates.data.MangaUpdatesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaUpdatesScrobbler @Inject constructor(
	private val repository: MangaUpdatesRepository,
	db: MangaDatabase,
	mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.MANGAUPDATES, repository, mangaRepositoryFactory) {

	init {
		statuses[ScrobblingStatus.READING] = "0"
		statuses[ScrobblingStatus.PLANNED] = "1"
		statuses[ScrobblingStatus.COMPLETED] = "2"
		statuses[ScrobblingStatus.ON_HOLD] = "4"
		statuses[ScrobblingStatus.DROPPED] = "3" // "Unfinished" mapping
	}

	override suspend fun updateScrobblingInfo(
		mangaId: Long,
		rating: Float,
		status: ScrobblingStatus?,
		comment: String?,
	) {
		val entity = db.getScrobblingDao().find(scrobblerService.id, mangaId)
		requireNotNull(entity) { "Scrobbling info for manga $mangaId not found" }
		
		repository.updateRate(
			rateId = entity.id,
			mangaId = mangaId,
			rating = rating, // Assuming rating internally scales to whatever, MangaUpdates supports 0.0-10.0
			status = statuses[status],
			comment = comment,
		)
	}

	override suspend fun onAuthorized(user: ScrobblerUser) {
		// Sync functionality not completely ported for MU, but let's call it just in case
		// repository.syncLibraryFromRemote()
	}

	override suspend fun syncLibrary(): Int {
		return repository.syncLibraryFromRemote()
	}
}
