package org.skepsun.kototoro.local.data.output

import androidx.core.net.toFile
import androidx.core.net.toUri
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.parsers.model.Content

class LocalContentUtil(
	private val manga: Content,
) {

	init {
		require(manga.isLocal) { "Expected LOCAL source but ${manga.source} found" }
	}

	suspend fun deleteChapters(ids: Set<Long>) {
		val file = manga.url.toUri().toFile()
		if (file.isDirectory) {
			LocalContentDirOutput(file, manga).use { output ->
				output.deleteChapters(ids)
				output.finish()
			}
		} else {
			LocalContentZipOutput.filterChapters(file, manga, ids)
		}
	}
}
