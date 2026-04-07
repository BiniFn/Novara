package org.skepsun.kototoro.reader.novel.tts.engine

import org.skepsun.kototoro.reader.novel.tts.model.AudioData
import org.skepsun.kototoro.reader.novel.tts.model.Token

interface TTSEngine {
    suspend fun synthesize(token: Token): Result<AudioData>
}
