package org.skepsun.kototoro.scrobbling.common.data

import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserProfile

interface ScrobblerUserProfileRepository {

    suspend fun loadUserProfile(): ScrobblerUserProfile
}
