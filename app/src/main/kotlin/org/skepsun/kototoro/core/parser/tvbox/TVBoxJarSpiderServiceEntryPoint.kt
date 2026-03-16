package org.skepsun.kototoro.core.parser.tvbox

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TVBoxJarSpiderServiceEntryPoint {
	val legadoHttpClient: LegadoHttpClient
}
