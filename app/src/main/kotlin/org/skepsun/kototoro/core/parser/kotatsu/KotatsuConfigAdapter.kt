package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.config.ConfigKey as KTConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig as KTMangaSourceConfig
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.config.MangaSourceConfig

internal class KotatsuConfigAdapter(
	private val delegate: MangaSourceConfig,
) : KTMangaSourceConfig {

	override fun <T> get(key: KTConfigKey<T>): T {
		return delegate[key.toKototoro()]
	}
}

@Suppress("UNCHECKED_CAST")
internal fun <T> KTConfigKey<T>.toKototoro(): ConfigKey<T> = when (this) {
	is KTConfigKey.Domain -> ConfigKey.Domain(*presetValues) as ConfigKey<T>
	is KTConfigKey.ShowSuspiciousContent -> ConfigKey.ShowSuspiciousContent(defaultValue) as ConfigKey<T>
	is KTConfigKey.UserAgent -> ConfigKey.UserAgent(defaultValue) as ConfigKey<T>
	is KTConfigKey.SplitByTranslations -> ConfigKey.SplitByTranslations(defaultValue) as ConfigKey<T>
	is KTConfigKey.PreferredImageServer -> ConfigKey.PreferredImageServer(presetValues, defaultValue) as ConfigKey<T>
}
