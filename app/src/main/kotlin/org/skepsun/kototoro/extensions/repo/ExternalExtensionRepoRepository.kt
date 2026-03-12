package org.skepsun.kototoro.extensions.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.core.db.dao.ExternalExtensionRepoDao
import org.skepsun.kototoro.core.db.entity.ExternalExtensionRepoEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalExtensionRepoRepository @Inject constructor(
	private val dao: ExternalExtensionRepoDao,
	private val service: ExtensionRepoService,
) {

	fun observeByType(type: ExternalExtensionType): Flow<List<ExternalExtensionRepo>> {
		return dao.observeByType(type).map { list -> list.map { it.toDomain() } }
	}

	suspend fun getByType(type: ExternalExtensionType): List<ExternalExtensionRepo> {
		return dao.getByType(type).map { it.toDomain() }
	}

	suspend fun addRepo(type: ExternalExtensionType, indexUrl: String): AddRepoResult {
		val normalizedIndexUrl = service.normalizeIndexUrl(indexUrl) ?: return AddRepoResult.InvalidUrl
		val baseUrl = service.baseUrlFromIndexUrl(normalizedIndexUrl)
		if (dao.get(type, baseUrl) != null) {
			return AddRepoResult.RepoAlreadyExists
		}
		val repo = service.fetchRepoDetails(baseUrl, type) ?: return AddRepoResult.InvalidUrl
		val duplicate = dao.getByFingerprint(type, repo.signingKeyFingerprint)
		if (duplicate != null) {
			return AddRepoResult.DuplicateFingerprint(duplicate.toDomain())
		}
		dao.upsert(repo.toEntity())
		return AddRepoResult.Success(repo)
	}

	suspend fun delete(repo: ExternalExtensionRepo) {
		dao.delete(repo.type, repo.baseUrl)
	}

	suspend fun refresh(type: ExternalExtensionType) {
		getByType(type).forEach { refresh(it) }
	}

	suspend fun refresh(repo: ExternalExtensionRepo) {
		val refreshed = service.fetchRepoDetails(repo.baseUrl, repo.type)
		val now = System.currentTimeMillis()
		val entity = if (refreshed != null) {
			refreshed.copy(
				createdAt = repo.createdAt,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
			).toEntity()
		} else {
			repo.copy(
				updatedAt = now,
				lastError = "Failed to refresh repository metadata",
			).toEntity()
		}
		dao.upsert(entity)
	}

	suspend fun getAvailableExtensions(type: ExternalExtensionType): List<RepoAvailableExtension> = coroutineScope {
		getByType(type)
			.map { repo -> async { service.fetchAvailableExtensions(repo) } }
			.awaitAll()
			.flatten()
			.distinctBy { it.pkgName }
			.sortedWith(compareBy<RepoAvailableExtension> { it.lang }.thenBy { it.name.lowercase() })
	}

	sealed interface AddRepoResult {
		data class Success(val repo: ExternalExtensionRepo) : AddRepoResult
		data class DuplicateFingerprint(val existingRepo: ExternalExtensionRepo) : AddRepoResult
		data object InvalidUrl : AddRepoResult
		data object RepoAlreadyExists : AddRepoResult
	}
}

private fun ExternalExtensionRepoEntity.toDomain(): ExternalExtensionRepo {
	return ExternalExtensionRepo(
		type = type,
		baseUrl = baseUrl,
		name = name,
		shortName = shortName,
		website = website,
		signingKeyFingerprint = signingKeyFingerprint,
		createdAt = createdAt,
		updatedAt = updatedAt,
		lastSuccessAt = lastSuccessAt,
		lastError = lastError,
	)
}

private fun ExternalExtensionRepo.toEntity(): ExternalExtensionRepoEntity {
	return ExternalExtensionRepoEntity(
		type = type,
		baseUrl = baseUrl,
		name = name,
		shortName = shortName,
		website = website,
		signingKeyFingerprint = signingKeyFingerprint,
		createdAt = createdAt,
		updatedAt = updatedAt,
		lastSuccessAt = lastSuccessAt,
		lastError = lastError,
	)
}
