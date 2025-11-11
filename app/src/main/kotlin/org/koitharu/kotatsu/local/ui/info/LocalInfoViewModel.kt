package org.skepsun.kototoro.local.ui.info

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.model.parcelable.ParcelableManga
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.computeSize
import org.skepsun.kototoro.core.util.ext.require
import org.skepsun.kototoro.core.util.ext.toFileOrNull
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.local.data.LocalStorageManager
import org.skepsun.kototoro.local.domain.DeleteReadChaptersUseCase
import javax.inject.Inject

@HiltViewModel
class LocalInfoViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val localMangaRepository: LocalMangaRepository,
	private val storageManager: LocalStorageManager,
	private val deleteReadChaptersUseCase: DeleteReadChaptersUseCase,
) : BaseViewModel() {

	private val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	val isCleaningUp = MutableStateFlow(false)
	val onCleanedUp = MutableEventFlow<Pair<Int, Long>>()

	val path = MutableStateFlow<String?>(null)
	val size = MutableStateFlow(-1L)
	val availableSize = MutableStateFlow(-1L)

	init {
		computeSize()
	}

	fun cleanup() {
		launchJob(Dispatchers.Default) {
			try {
				isCleaningUp.value = true
				val oldSize = size.value
				val chaptersCount = deleteReadChaptersUseCase.invoke(manga)
				computeSize().join()
				val newSize = size.value
				onCleanedUp.call(chaptersCount to oldSize - newSize)
			} finally {
				isCleaningUp.value = false
			}
		}
	}

	private fun computeSize() = launchLoadingJob(Dispatchers.Default) {
		val file = manga.url.toUri().toFileOrNull() ?: localMangaRepository.findSavedManga(manga)?.file
		requireNotNull(file)
		path.value = file.path
		size.value = file.computeSize()
		availableSize.value = storageManager.computeAvailableSize()
	}
}
