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
import org.skepsun.kototoro.local.data.input.LocalContentParser
import org.skepsun.kototoro.local.domain.model.LocalContent
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Import mode for directory import
 */
enum class ImportMode {
	/** Import a single manga - the folder itself is the manga, subdirectories are chapters */
	SINGLE_MANGA,
	/** Import multiple manga - each subdirectory is a separate manga */
	MULTIPLE_MANGA
}

@Reusable
class SingleContentImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val storageManager: LocalStorageManager,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalContent?>,
) {

	private val contentResolver = context.contentResolver

	/**
	 * Import files (CBZ/ZIP archives)
	 */
	suspend fun import(uri: Uri, importKind: LocalImportKind? = null): List<LocalContent> {
		val results = if (isDirectory(uri)) {
			// For file import, auto-detect (for backward compatibility)
			importDirectoryAuto(uri, importKind)
		} else {
			listOf(importFile(uri, importKind))
		}
		results.forEach { localStorageChanges.emit(it) }
		return results
	}

	/**
	 * Import directory with specified mode
	 */
	suspend fun import(uri: Uri, mode: ImportMode, importKind: LocalImportKind? = null): List<LocalContent> {
		val results = if (isDirectory(uri)) {
			when (mode) {
				ImportMode.SINGLE_MANGA -> importDirectorySingle(uri, importKind)
				ImportMode.MULTIPLE_MANGA -> importDirectoryMultiple(uri, importKind)
			}
		} else {
			listOf(importFile(uri, importKind))
		}
		results.forEach { localStorageChanges.emit(it) }
		return results
	}

	private suspend fun importFile(uri: Uri, overrideKind: LocalImportKind? = null): LocalContent = withContext(Dispatchers.IO) {
		val contentResolver = storageManager.contentResolver
		val name = contentResolver.resolveName(uri) ?: throw IOException("Cannot fetch name from uri: $uri")
		if (!LocalImportSupport.supportsFileName(name)) {
			throw UnsupportedFileException("Unsupported file $name on $uri")
		}
		val kind = LocalImportSupport.classifyFileName(name)
		if (overrideKind != null && overrideKind != kind && !hasZipExtension(name)) {
			throw UnsupportedFileException("File $name does not match selected content type: $overrideKind")
		}
		val outputDir = getOutputDir(overrideKind ?: kind)
		val dest = if (hasZipExtension(name)) {
			File(outputDir, name)
		} else {
			File(outputDir, LocalImportSupport.contentFolderName(name)).apply { mkdirs() }
				.let { File(it, name) }
		}
		runInterruptible {
			contentResolver.openSource(uri)
		}.use { source ->
			dest.sink().buffer().use { output ->
				output.writeAllCancellable(source)
			}
		}
		val parseTarget = if (hasZipExtension(name)) dest else requireNotNull(dest.parentFile)
		LocalContentParser(parseTarget).getContent(withDetails = false)
	}

	/**
	 * Auto-detect import mode (for backward compatibility with file import)
	 */
	private suspend fun importDirectoryAuto(uri: Uri, overrideKind: LocalImportKind? = null): List<LocalContent> {
		// Default to single manga mode for auto-detect
		return importDirectorySingle(uri, overrideKind)
	}

	/**
	 * Import as single manga - the selected folder is one manga, subdirectories are chapters
	 */
	private suspend fun importDirectorySingle(uri: Uri, overrideKind: LocalImportKind? = null): List<LocalContent> {
		val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
			"Provided uri $uri is not a tree"
		}
		val childFiles = root.listFiles()
		val kind = overrideKind ?: classifyImportKind(childFiles.mapNotNull { it.name })
		val dest = File(getOutputDir(kind), root.requireName())
		dest.mkdir()
		for (docFile in childFiles) {
			docFile.copyTo(dest)
		}
		return listOf(LocalContentParser(dest).getContent(withDetails = false))
	}

	/**
	 * Import as multiple manga - each subdirectory is a separate manga
	 */
	private suspend fun importDirectoryMultiple(uri: Uri, overrideKind: LocalImportKind? = null): List<LocalContent> {
		val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
			"Provided uri $uri is not a tree"
		}
		val childFiles = root.listFiles()
		val subDirs = childFiles.filter { it.isDirectory }
		val importableFiles = childFiles.filter { 
			it.isFile && LocalImportSupport.supportsFileName(it.name ?: "") &&
			(overrideKind == null || hasZipExtension(it.name ?: "") || LocalImportSupport.classifyFileName(it.name ?: "") == overrideKind)
		}
		
		val results = mutableListOf<LocalContent>()
		
		// Import each subdirectory as a separate manga
		for (folder in subDirs) {
			val folderChildren = folder.listFiles()
			val kind = overrideKind ?: classifyImportKind(folderChildren.mapNotNull { it.name })
			val dest = File(getOutputDir(kind), folder.requireName())
			dest.mkdir()
			// Copy folder contents directly to dest (not the folder itself)
			// to avoid double-nesting like "漫画1/漫画1/图片.webp"
			for (docFile in folderChildren) {
				docFile.copyTo(dest)
			}
			runCatching { LocalContentParser(dest).getContent(withDetails = false) }
				.getOrNull()
				?.let { results.add(it) }
		}
		
		// Also import any supported files at the top level
		for (file in importableFiles) {
			val name = file.name ?: continue
			val kind = overrideKind ?: LocalImportSupport.classifyFileName(name)
			val destRoot = getOutputDir(kind)
			val dest = if (hasZipExtension(name)) {
				File(destRoot, name)
			} else {
				File(destRoot, LocalImportSupport.contentFolderName(name)).apply { mkdirs() }
					.let { File(it, name) }
			}
			file.source().use { input ->
				dest.sink().buffer().use { output ->
					output.writeAllCancellable(input)
				}
			}
			val parseTarget = if (hasZipExtension(name)) dest else requireNotNull(dest.parentFile)
			runCatching { LocalContentParser(parseTarget).getContent(withDetails = false) }
				.getOrNull()
				?.let { results.add(it) }
		}
		
		return results
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

	private fun classifyImportKind(fileNames: Collection<String>): LocalImportKind {
		if (fileNames.any { LocalImportSupport.classifyFileName(it) == LocalImportKind.VIDEO }) {
			return LocalImportKind.VIDEO
		}
		if (fileNames.any { LocalImportSupport.classifyFileName(it) == LocalImportKind.NOVEL }) {
			return LocalImportKind.NOVEL
		}
		return LocalImportKind.MANGA
	}

	private suspend fun getOutputDir(kind: LocalImportKind): File {
		val dir = when (kind) {
			LocalImportKind.MANGA -> storageManager.getDefaultWriteableDir()
			LocalImportKind.NOVEL -> storageManager.getDefaultNovelWriteableDir()
			LocalImportKind.VIDEO -> storageManager.getVideoRoot()
		}
		return dir ?: throw IOException("External files dir unavailable")
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
