package org.skepsun.kototoro.tracking.discovery

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.skepsun.kototoro.tracking.discovery.data.AppSettingsPreferredTrackingSiteProvider
import org.skepsun.kototoro.tracking.discovery.data.DefaultTrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.PreferredTrackingSiteProvider
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TrackingDiscoveryModule {

	@Binds
	@Singleton
	fun bindTrackingSiteDiscoveryService(
		impl: DefaultTrackingSiteDiscoveryService,
	): TrackingSiteDiscoveryService

	@Binds
	@Singleton
	fun bindPreferredTrackingSiteProvider(
		impl: AppSettingsPreferredTrackingSiteProvider,
	): PreferredTrackingSiteProvider
}
