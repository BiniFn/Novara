package org.skepsun.kototoro.reader.novel.tts

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
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
        for (i in startIndex until tokens.size) {
            val token = tokens[i]
            
            // Acquire a permit to ensure we only process at most X requests simultaneously
            Log.d(TAG, "Acquiring semaphore for token index: $i")
            semaphore.acquire()
            Log.d(TAG, "Semaphore acquired for token index: $i")
            
            launch {
                try {
                    Log.d(TAG, "Starting synthesis for token index: $i, text: ${token.text.take(10)}")
                    // For PAUSE tokens, we do NOT use network / cache. We just let the engine synthesize directly (which handles internal PAUSE creation).
                    if (token.type == TokenType.PAUSE) {
                        val audio = engine.synthesize(token).getOrNull()
                        if (audio != null) {
                            send(i to audio)
                        }
                        return@launch
                    }

                    // For Text tokens:
                    val currentVoiceId = token.speaker?.voiceId ?: voiceId
                    val key = cache.buildCacheKey(token, engineId, currentVoiceId, speed, pitch)
                    
                    // 1. Check cache first
                    var audio = cache.get(key)
                    
                    // 2. If not cached, synthesize
                    if (audio == null) {
                        Log.d(TAG, "Cache miss for token index: $i. Calling engine..")
                        audio = engine.synthesize(token).getOrNull()
                        // 3. Cache the newly synthesized audio
                        if (audio != null) {
                            Log.d(TAG, "Synthesized successfully, caching audio for token $i")
                            cache.put(key, audio)
                        } else {
                            Log.e(TAG, "Engine failed to synthesize token index $i")
                        }
                    } else {
                        Log.d(TAG, "Cache hit for token index: $i")
                    }
                    
                    if (audio != null) {
                        // 4. Send the result down the stream
                        Log.d(TAG, "Sending audio down channel for token $i")
                        send(i to audio)
                    }
                } finally {
                    semaphore.release()
                    Log.d(TAG, "Semaphore released for token index: $i")
                }
            }
        }
    }
}
