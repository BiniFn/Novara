package org.skepsun.kototoro.core.exceptions

import org.skepsun.kototoro.core.model.UnknownMangaSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper

class CloudFlareBlockedException(
	override val url: String,
	source: MangaSource?,
) : CloudFlareException("Blocked by CloudFlare", CloudFlareHelper.PROTECTION_BLOCKED) {

	override val source: MangaSource = source ?: UnknownMangaSource
}
