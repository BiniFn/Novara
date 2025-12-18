package org.skepsun.kototoro.local.data.importer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import org.skepsun.kototoro.core.exceptions.UnsupportedFileException
import org.skepsun.kototoro.core.util.ext.openSource
import org.skepsun.kototoro.core.util.ext.resolveName
import org.skepsun.kototoro.core.util.ext.writeAllCancellable
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.local.data.hasZipExtension
import org.skepsun.kototoro.local.data.input.LocalMangaParser
import org.skepsun.kototoro.local.domain.model.LocalManga
import java.io.File
import java.io.IOException
import javax.inject.Inject

@Reusable
class SingleMangaImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val storageManager: LocalStorageManager,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
) {

	private val contentResolver = context.contentResolver

	suspend fun import(uri: Uri): List<LocalManga> {
		val results = if (isDirectory(uri)) {
			importDirectory(uri)
		} else {
			listOf(importFile(uri))
		}
		results.forEach { localStorageChanges.emit(it) }
		return results
	}

	private suspend fun importFile(uri: Uri): LocalManga = withContext(Dispatchers.IO) {
		val contentResolver = storageManager.contentResolver
		val name = contentResolver.resolveName(uri) ?: throw IOException("Cannot fetch name from uri: $uri")
		if (!hasZipExtension(name)) {
			throw UnsupportedFileException("Unsupported file $name on $uri")
		}
		val dest = File(getOutputDir(), name)
		runInterruptible {
			contentResolver.openSource(uri)
		}.use { source ->
			dest.sink().buffer().use { output ->
				output.writeAllCancellable(source)
			}
		}
		LocalMangaParser(dest).getManga(withDetails = false)
	}

	private suspend fun importDirectory(uri: Uri): List<LocalManga> {
		val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
			"Provided uri $uri is not a tree"
		}
		val childFiles = root.listFiles()
		val subDirs = childFiles.filter { it.isDirectory }
		val hasTopLevelZip = childFiles.any { it.isFile && hasZipExtension(it.name ?: "") }
		return if (subDirs.size > 1 && !hasTopLevelZip) {
			// Treat each sub-folder as an individual manga
			subDirs.mapNotNull { folder ->
				val dest = File(getOutputDir(), folder.requireName())
				dest.mkdir()
				folder.copyTo(dest)
				runCatching { LocalMangaParser(dest).getManga(withDetails = false) }.getOrNull()
			}
		} else {
			val dest = File(getOutputDir(), root.requireName())
			dest.mkdir()
			for (docFile in childFiles) {
				docFile.copyTo(dest)
			}
			listOf(LocalMangaParser(dest).getManga(withDetails = false))
		}
	}

	private suspend fun DocumentFile.copyTo(destDir: File) {
		if (isDirectory) {
			val subDir = File(destDir, requireName())
			subDir.mkdir()
			for (docFile in listFiles()) {
				docFile.copyTo(subDir)
			}
		} else {
			source().use { input ->
				File(destDir, requireName()).sink().buffer().use { output ->
					output.writeAllCancellable(input)
				}
			}
		}
	}

	private suspend fun getOutputDir(): File {
		return storageManager.getDefaultWriteableDir() ?: throw IOException("External files dir unavailable")
	}

	private suspend fun DocumentFile.source() = runInterruptible(Dispatchers.IO) {
		contentResolver.openSource(uri)
	}

	private fun DocumentFile.requireName(): String {
		return name ?: throw IOException("Cannot fetch name from uri: $uri")
	}

	private fun isDirectory(uri: Uri): Boolean {
		return runCatching {
			DocumentFile.fromTreeUri(context, uri)
		}.isSuccess
	}
}
