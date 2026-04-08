package org.skepsun.kototoro.reader.novel.tts

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import org.skepsun.kototoro.reader.novel.tts.engine.TTSEngine
import org.skepsun.kototoro.reader.novel.tts.model.AudioData
import org.skepsun.kototoro.reader.novel.tts.model.Token
import org.skepsun.kototoro.reader.novel.tts.model.TokenType
import org.skepsun.kototoro.reader.novel.tts.model.TtsSession
import android.util.Log

class Prefetcher(
    private val engine: TTSEngine,
    private val cache: TtsCache,
    private val context: Context
) {
    private val semaphore = Semaphore(3) // 保护 HTTP 免于 429 Too Many Requests

    companion object {
        private const val TAG = "TtsPrefetcher"
    }

    fun prefetch(
        session: TtsSession,
        tokens: List<Token>,
        startIndex: Int,
        engineId: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    ): Flow<Pair<Int, AudioData>> = channelFlow {
        val jobs = kotlinx.coroutines.channels.Channel<kotlinx.coroutines.Deferred<Pair<Int, AudioData>?>>(capacity = tokens.size - startIndex + 1)
        
        launch {
            for (i in startIndex until tokens.size) {
                val token = tokens[i]
                semaphore.acquire()
                val deferred = async {
                    try {
                        Log.d(TAG, "Starting synthesis for token index: $i, text: ${token.text.take(10)}")
                        if (token.type == TokenType.PAUSE) {
                            val audio = engine.synthesize(token).getOrNull()
                            if (audio != null) i to audio else null
                        } else {
                            val currentVoiceId = token.speaker?.voiceId ?: voiceId
                            val key = cache.buildCacheKey(token, engineId, currentVoiceId, speed, pitch)
                            var audio = cache.get(key)
                            if (audio == null) {
                                audio = engine.synthesize(token).getOrNull()
                                if (audio != null) {
                                    cache.put(key, audio)
                                }
                            }
                            if (audio != null) i to audio else null
                        }
                    } finally {
                        semaphore.release()
                    }
                }
                // Session may have been cancelled while we were synthesizing;
                // channel would be closed, so bail out gracefully.
                try {
                    jobs.send(deferred)
                } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                    Log.d(TAG, "Jobs channel closed, stopping prefetch at index $i")
                    return@launch
                }
            }
            jobs.close()
        }

        for (deferred in jobs) {
            val result = deferred.await()
            if (result != null) {
                try {
                    send(result)
                } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                    Log.d(TAG, "Output channel closed, stopping emission")
                    break
                }
            }
        }
    }
}

