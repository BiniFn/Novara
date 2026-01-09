package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.config.ConfigKey as KTConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig as KTMangaSourceConfig
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.config.MangaSourceConfig

internal class KotatsuConfigAdapter(
	private val delegate: MangaSourceConfig,
) : KTMangaSourceConfig {

	@Suppress("UNCHECKED_CAST")
	override fun <T> get(key: KTConfigKey<T>): T {
		val mapped: ConfigKey<T> = when (key) {
			is KTConfigKey.Domain -> ConfigKey.Domain(*key.presetValues) as ConfigKey<T>
			is KTConfigKey.ShowSuspiciousContent -> ConfigKey.ShowSuspiciousContent(key.defaultValue) as ConfigKey<T>
			is KTConfigKey.UserAgent -> ConfigKey.UserAgent(key.defaultValue) as ConfigKey<T>
			is KTConfigKey.SplitByTranslations -> ConfigKey.SplitByTranslations(key.defaultValue) as ConfigKey<T>
			is KTConfigKey.PreferredImageServer -> ConfigKey.PreferredImageServer(key.presetValues, key.defaultValue) as ConfigKey<T>
		}
		return delegate[mapped]
	}
}
