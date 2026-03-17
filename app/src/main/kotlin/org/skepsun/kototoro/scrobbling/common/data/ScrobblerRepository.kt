package org.skepsun.kototoro.scrobbling.common.data

import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser

interface ScrobblerRepository {

	val oauthUrl: String

	val isAuthorized: Boolean

	val cachedUser: ScrobblerUser?

	suspend fun authorize(code: String?)

	suspend fun loadUser(): ScrobblerUser

	fun logout()

	suspend fun unregister(mangaId: Long)

	suspend fun findContent(query: String, offset: Int): List<ScrobblerContent>

	suspend fun getContentInfo(id: Long): ScrobblerContentInfo

	suspend fun createRate(mangaId: Long, scrobblerContentId: Long)

	suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int)

	suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?)
}
