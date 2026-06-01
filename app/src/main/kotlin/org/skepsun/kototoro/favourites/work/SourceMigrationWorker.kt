package org.skepsun.kototoro.favourites.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.alternatives.domain.MigrateUseCase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.db.entity.toContentTag
import org.skepsun.kototoro.core.util.ext.checkNotificationPermission
import org.skepsun.kototoro.core.util.ext.trySetForeground
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.favourites.data.FavouriteSourcesRepository
import org.skepsun.kototoro.favourites.domain.MigrationItem
import org.skepsun.kototoro.favourites.domain.MigrationStatus
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.SearchV2Helper
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "SourceMigration"

@HiltWorker
class SourceMigrationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val favouriteSourcesRepository: FavouriteSourcesRepository,
    private val sourcesRepository: ContentSourcesRepository,
    private val searchHelperFactory: SearchV2Helper.Factory,
    private val migrateUseCase: MigrateUseCase,
    private val notificationFactoryFactory: SourceMigrationNotificationFactory.Factory,
) : CoroutineWorker(appContext, params) {

    private val notificationFactory = notificationFactoryFactory.create(
        UUID.nameUUIDFromBytes(id.toString().toByteArray()),
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notificationFactory.createProgress(1, 0, 0, 0, null)
        return buildForegroundInfo(notification)
    }

    override suspend fun doWork(): Result {
        val fromSourceName = inputData.getString(KEY_FROM_SOURCE) ?: return Result.failure()
        val toSourceName = inputData.getString(KEY_TO_SOURCE) ?: return Result.failure()
        val concurrency = inputData.getInt(KEY_CONCURRENCY, 3)

        Log.d(TAG, "Worker started: from=$fromSourceName, to=$toSourceName, concurrency=$concurrency")

        val allSources = sourcesRepository.getAllAvailableSourcesForListing()
        val toSource = allSources.find { it.name == toSourceName }
        Log.d(TAG, "Resolved target source: ${toSource?.javaClass?.simpleName}(${toSource != null})")
        if (toSource == null) {
            Log.e(TAG, "Target source not found: $toSourceName")
            return Result.failure()
        }

        val favouriteContents = favouriteSourcesRepository.getFavouriteContentsBySource(fromSourceName)
        Log.d(TAG, "Favorites from source '$fromSourceName': ${favouriteContents.size} items")
        favouriteContents.take(3).forEach { fc ->
            Log.d(TAG, "  item: id=${fc.manga.id} title=${fc.manga.title} tags=${fc.tags.size}")
        }
        if (favouriteContents.isEmpty()) {
            Log.d(TAG, "No favorites to migrate")
            return Result.success()
        }

        val hasPermission = applicationContext.checkNotificationPermission(CHANNEL_ID_SOURCE_MIGRATION)
        Log.d(TAG, "Notification permission: $hasPermission")
        val foregroundActive = if (hasPermission) {
            val ok = trySetForeground()
            Log.d(TAG, "Foreground started: $ok")
            ok
        } else {
            false
        }

        return withContext(Dispatchers.IO) {
            val items = favouriteContents.map { fc ->
                MigrationItem(mangaId = fc.manga.id, title = fc.manga.title)
            }.toMutableList()

            val completedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val notFoundCount = AtomicInteger(0)
            val semaphore = Semaphore(concurrency)
            val searchHelper = searchHelperFactory.create(toSource)
            Log.d(TAG, "SearchHelper created for: ${toSource.name}")

            publishProgress(items.size, 0, 0, 0, foregroundActive)

            coroutineScope {
                favouriteContents.mapIndexed { index, fc ->
                    async {
                        semaphore.withPermit {
                            if (isStopped) return@async

                            val item = items[index]
                            Log.d(TAG, "[${index + 1}/${items.size}] Searching: ${fc.manga.title}")
                            items[index] = item.copy(status = MigrationStatus.SEARCHING)
                            publishProgress(
                                items.size, completedCount.get(), failedCount.get(),
                                notFoundCount.get(), foregroundActive,
                            )

                            val searchResults = runCatchingCancellable {
                                searchHelper(fc.manga.title, SearchKind.TITLE, null)
                            }.onFailure { e ->
                                Log.e(TAG, "[${index + 1}/${items.size}] Search error '${fc.manga.title}': ${e.message}", e)
                            }.getOrNull()

                            if (searchResults == null || searchResults.manga.isEmpty()) {
                                Log.d(TAG, "[${index + 1}/${items.size}] NOT FOUND: ${fc.manga.title}")
                                items[index] = item.copy(status = MigrationStatus.NOT_FOUND, errorMessage = "No match on target source")
                                notFoundCount.incrementAndGet()
                                publishProgress(
                                    items.size, completedCount.get(), failedCount.get(),
                                    notFoundCount.get(), foregroundActive,
                                )
                                return@async
                            }

                            val match = searchResults.manga.first()
                            Log.d(TAG, "[${index + 1}/${items.size}] Found match: ${match.title} (id=${match.id})")
                            items[index] = item.copy(status = MigrationStatus.MIGRATING)

                            // Build Content from DB entity for the MigrateUseCase
                            val oldContent = fc.manga.toContent(
                                tags = fc.tags.mapTo(mutableSetOf()) { it.toContentTag() },
                                chapters = null,
                            )

                            val result = runCatchingCancellable {
                                migrateUseCase(oldContent, match)
                            }
                            if (result.isSuccess) {
                                items[index] = item.copy(status = MigrationStatus.SUCCESS)
                                completedCount.incrementAndGet()
                                Log.d(TAG, "[${index + 1}/${items.size}] SUCCESS: ${fc.manga.title} -> ${match.title}")
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "unknown"
                                Log.e(TAG, "[${index + 1}/${items.size}] FAILED: ${fc.manga.title} error=$error")
                                items[index] = item.copy(status = MigrationStatus.ERROR, errorMessage = error)
                                failedCount.incrementAndGet()
                            }
                            publishProgress(
                                items.size, completedCount.get(), failedCount.get(),
                                notFoundCount.get(), foregroundActive,
                            )
                        }
                    }
                }.awaitAll()
            }

            val completed = completedCount.get()
            val failed = failedCount.get()
            val notFound = notFoundCount.get()
            Log.d(TAG, "Migration finished: total=${items.size} success=$completed failed=$failed notFound=$notFound")

            if (foregroundActive) {
                setForeground(
                    buildForegroundInfo(
                        notificationFactory.createFinished(items.size, completed, failed, notFound),
                    ),
                )
            }

            val progressData = workDataOf(
                Pair(KEY_FINISHED, true),
                Pair(KEY_TOTAL, items.size),
                Pair(KEY_COMPLETED, completed),
                Pair(KEY_FAILED, failed),
                Pair(KEY_NOT_FOUND, notFound),
            )
            setProgress(progressData)
            Result.success(progressData)
        }
    }

    private fun buildForegroundInfo(notification: android.app.Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private suspend fun publishProgress(
        total: Int, completed: Int, failed: Int, notFound: Int,
        updateNotification: Boolean,
    ) {
        setProgress(
            workDataOf(
                Pair(KEY_TOTAL, total),
                Pair(KEY_COMPLETED, completed),
                Pair(KEY_FAILED, failed),
                Pair(KEY_NOT_FOUND, notFound),
            ),
        )
        if (updateNotification && !isStopped) {
            setForeground(
                buildForegroundInfo(
                    notificationFactory.createProgress(total, completed, failed, notFound, null),
                ),
            )
        }
    }

    companion object {
        const val KEY_FROM_SOURCE = "from_source"
        const val KEY_TO_SOURCE = "to_source"
        const val KEY_CONCURRENCY = "concurrency"
        const val KEY_FINISHED = "finished"
        const val KEY_TOTAL = "total"
        const val KEY_COMPLETED = "completed"
        const val KEY_FAILED = "failed"
        const val KEY_NOT_FOUND = "not_found"
        const val WORK_TAG = "source_migration"
    }
}
