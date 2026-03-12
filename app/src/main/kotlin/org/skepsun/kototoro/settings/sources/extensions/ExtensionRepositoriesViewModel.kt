package org.skepsun.kototoro.settings.sources.extensions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.require
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepoRepository
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import javax.inject.Inject

@HiltViewModel
class ExtensionRepositoriesViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repoRepository: ExternalExtensionRepoRepository,
) : BaseViewModel() {

	val type: ExternalExtensionType = enumValueOf(savedStateHandle.require<String>(ARG_EXTENSION_TYPE))

	val repos: StateFlow<List<ExternalExtensionRepo>> = repoRepository.observeByType(type)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	val repoCount: StateFlow<Int> = repos.map { it.size }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val onMessage = MutableEventFlow<String>()
	val onTrustPrompt = MutableEventFlow<ExternalExtensionRepo>()

	fun addRepo(indexUrl: String) {
		launchLoadingJob(Dispatchers.IO) {
			when (val result = repoRepository.prepareAddRepo(type, indexUrl)) {
				is ExternalExtensionRepoRepository.PrepareAddRepoResult.Ready -> {
					onTrustPrompt.call(result.repo)
				}

				is ExternalExtensionRepoRepository.PrepareAddRepoResult.DuplicateFingerprint -> {
					onMessage.call("Signing fingerprint already used by ${result.existingRepo.displayName}")
				}

				ExternalExtensionRepoRepository.PrepareAddRepoResult.InvalidUrl -> {
					onMessage.call("Invalid repository URL")
				}

				ExternalExtensionRepoRepository.PrepareAddRepoResult.RepoAlreadyExists -> {
					onMessage.call("Repository already exists")
				}
			}
		}
	}

	fun confirmAddRepo(repo: ExternalExtensionRepo) {
		launchLoadingJob(Dispatchers.IO) {
			when (val result = repoRepository.confirmAddRepo(repo)) {
				is ExternalExtensionRepoRepository.AddRepoResult.Success -> {
					onMessage.call("Added ${result.repo.displayName}")
				}

				is ExternalExtensionRepoRepository.AddRepoResult.DuplicateFingerprint -> {
					onMessage.call("Signing fingerprint already used by ${result.existingRepo.displayName}")
				}

				ExternalExtensionRepoRepository.AddRepoResult.InvalidUrl -> {
					onMessage.call("Invalid repository URL")
				}

				ExternalExtensionRepoRepository.AddRepoResult.RepoAlreadyExists -> {
					onMessage.call("Repository already exists")
				}
			}
		}
	}

	fun deleteRepo(repo: ExternalExtensionRepo) {
		launchLoadingJob(Dispatchers.IO) {
			repoRepository.delete(repo)
			onMessage.call("Removed ${repo.displayName}")
		}
	}

	fun refresh() {
		launchLoadingJob(Dispatchers.IO) {
			repoRepository.refresh(type)
		}
	}
}
