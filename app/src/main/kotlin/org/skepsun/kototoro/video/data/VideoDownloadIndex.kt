package org.skepsun.kototoro.video.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@Singleton
class VideoDownloadIndex @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val changesFlow = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    val changes: SharedFlow<Long>
        get() = changesFlow

    fun put(mangaId: Long, chapterId: Long, path: String) {
        prefs.edit().putString(key(mangaId, chapterId), path).apply()
        changesFlow.tryEmit(mangaId)
    }

    fun remove(mangaId: Long, chapterId: Long) {
        prefs.edit().remove(key(mangaId, chapterId)).apply()
        changesFlow.tryEmit(mangaId)
    }

    fun getFile(mangaId: Long, chapterId: Long): File? {
        val path = prefs.getString(key(mangaId, chapterId), null) ?: return null
        return File(path).takeIf { it.exists() && it.isFile }
    }

    fun getDownloadedChapterIds(mangaId: Long): Set<Long> {
        val prefix = "$mangaId:"
        val result = LinkedHashSet<Long>()
        prefs.all.forEach { (k, v) ->
            if (!k.startsWith(prefix)) return@forEach
            val chapterId = k.substringAfter(prefix).toLongOrNull() ?: return@forEach
            val path = v as? String ?: return@forEach
            val file = File(path)
            if (file.exists() && file.isFile) {
                result.add(chapterId)
            } else {
                // 清理已失效的索引，避免误判为已下载
                prefs.edit().remove(k).apply()
            }
        }
        return result
    }

    private fun key(mangaId: Long, chapterId: Long) = "$mangaId:$chapterId"

    private companion object {
        private const val PREFS_NAME = "video_download_index"
    }
}
