package org.skepsun.kototoro.settings.users

import javax.inject.Inject
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerUserProfileRepository
import org.skepsun.kototoro.scrobbling.common.domain.ScrobblerRepositoryMap
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserProfile

class TrackingUserAccountSummaryProvider @Inject constructor(
    private val repositoriesMap: ScrobblerRepositoryMap,
) {

    suspend fun load(service: ScrobblerService): ScrobblerUserProfile {
        val repository = repositoriesMap[service]
        return if (repository is ScrobblerUserProfileRepository) {
            repository.loadUserProfile()
        } else {
            ScrobblerUserProfile(user = repository.loadUser())
        }
    }
}
