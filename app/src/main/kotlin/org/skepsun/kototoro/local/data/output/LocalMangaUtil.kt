package org.skepsun.kototoro.local.data.output

import androidx.core.net.toFile
import androidx.core.net.toUri
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.parsers.model.Content
import java.io.File

class LocalContentUtil(
	private val manga: Content,
	private val file: File,
) {

	suspend fun deleteChapters(ids: Set<Long>) {
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
