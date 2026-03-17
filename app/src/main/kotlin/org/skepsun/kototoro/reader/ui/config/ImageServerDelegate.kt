package org.skepsun.kototoro.reader.ui.config

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.util.mapToArray
import org.skepsun.kototoro.parsers.util.suspendlazy.getOrNull
import org.skepsun.kototoro.parsers.util.suspendlazy.suspendLazy
import kotlin.coroutines.resume

class ImageServerDelegate(
	private val mangaRepositoryFactory: ContentRepository.Factory,
	private val mangaSource: ContentSource?,
) {

	private val repositoryLazy = suspendLazy {
		mangaRepositoryFactory.create(checkNotNull(mangaSource)) as ParserContentRepository
	}

	suspend fun isAvailable() = withContext(Dispatchers.Default) {
		repositoryLazy.getOrNull()?.let { repository ->
			repository.getConfigKeys().any { it is ConfigKey.PreferredImageServer }
		} == true
	}

	suspend fun getValue(): String? = withContext(Dispatchers.Default) {
		repositoryLazy.getOrNull()?.let { repository ->
			val key = repository.getConfigKeys().firstNotNullOfOrNull { it as? ConfigKey.PreferredImageServer }
			if (key != null) {
				key.presetValues[repository.getConfig()[key]]
			} else {
				null
			}
		}
	}

	suspend fun showDialog(context: Context): Boolean {
		val repository = withContext(Dispatchers.Default) {
			repositoryLazy.getOrNull()
		} ?: return false
		val key = repository.getConfigKeys().firstNotNullOfOrNull {
			it as? ConfigKey.PreferredImageServer
		} ?: return false
		val entries = key.presetValues.values.mapToArray {
			it ?: context.getString(R.string.automatic)
		}
		val entryValues = key.presetValues.keys.toTypedArray()
		val config = repository.getConfig()
		val initialValue = config[key]
		var currentValue = initialValue
		val changed = suspendCancellableCoroutine { cont ->
			val dialog = MaterialAlertDialogBuilder(context)
				.setTitle(R.string.image_server)
				.setCancelable(true)
				.setSingleChoiceItems(entries, entryValues.indexOf(initialValue)) { _, i ->
					currentValue = entryValues[i]
				}.setNegativeButton(android.R.string.cancel) { dialog, _ ->
					dialog.cancel()
				}.setPositiveButton(android.R.string.ok) { _, _ ->
					if (currentValue != initialValue) {
						config[key] = currentValue
						cont.resume(true)
					} else {
						cont.resume(false)
					}
				}.setOnCancelListener {
					cont.resume(false)
				}.create()
			dialog.show()
			cont.invokeOnCancellation {
				dialog.cancel()
			}
		}
		if (changed) {
			repository.invalidateCache()
		}
		return changed
	}
}
