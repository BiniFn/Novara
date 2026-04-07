package org.skepsun.kototoro.reader.novel.tts

import org.skepsun.kototoro.reader.novel.tts.model.Speaker
import org.skepsun.kototoro.reader.novel.tts.model.Token
import org.skepsun.kototoro.reader.novel.tts.model.TokenType
import java.security.MessageDigest

object Tokenizer {

    private fun md5ToLong(input: String): Long {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[i].toLong() and 0xFFL)
        }
        return result
    }
    
    // 自动分配虚拟音色资源池 (实际应用时可注入用户选择的声学模型ID组)
    private val fallbackVoices = listOf("zh-CN-YunxiNeural", "zh-CN-XiaoxiaoNeural", "zh-CN-YunjianNeural")
    
    private class SpeakerResolver {
        private val speakerMap = mutableMapOf<String, Speaker>()
        private var voiceIndex = 0

        fun resolve(name: String?, fallbackVoices: List<String>): Speaker? {
            if (name == null || name.isBlank()) return null
            return speakerMap.getOrPut(name) {
                // 轮询分配不同的音色以区分不同角色
                val voiceId = fallbackVoices[voiceIndex % fallbackVoices.size]
                voiceIndex++
                Speaker(name, voiceId)
            }
        }
    }

    /**
     * 将长文本切分为细粒度的 Token。
     * - 先按段落分 (\n\n)
     * - 按标点断句 (。！？)
     * - 针对中文长句按 (，、；) 继续切分
     * - 自动拆分对话与旁白
     * - 在合理的空隙处插入 PAUSE
     */
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0

        val paragraphs = text.split(Regex("(\\r?\\n)+"))
        for (p in paragraphs) {
            if (p.isBlank()) {
                index += p.length + 1 // +1 for the newline char (approximate, actual is \n or \r\n length but let's assume we map original char index)
                continue
            }

            // Simple sentence split by punctuation. This regex keeps the punctuation with the sentence part if we used lookarounds,
            // but for simplicity let's just split and keep the meaning. We will use a more robust split that captures the delimiters if needed.
            // For MVP, split by these punctuations: [。！？.!?]
            // We use Regex to match sentences.
            val sentenceRegex = Regex("([^。！？.!?]+[。！？.!?]*)")
            val sentences = sentenceRegex.findAll(p).map { it.value }.toList()
            val finalSentences = if (sentences.isEmpty()) listOf(p) else sentences

            val resolver = SpeakerResolver()

            for (s in finalSentences) {
                // 用于提取对话段落后文本中潜在说话人名称正则
                val speakerRegex = Regex("(\\S{1,4})(说|问|道|喊)")
                
                // For long sentences, split by comma/semicolon/dunhao
                val parts = if (s.length > 30) {
                    val partRegex = Regex("([^，、；,;]+[，、；,;]*)")
                    val subParts = partRegex.findAll(s).map { it.value }.toList()
                    if (subParts.isNotEmpty()) subParts else listOf(s)
                } else {
                    listOf(s)
                }

                for (part in parts) {
                    if (part.isBlank()) continue

                    // Parse dialogue
                    val isDialogue = part.contains("“") || part.contains("”")
                    
                    var speaker: Speaker? = null
                    val cleanText: String
                    
                    if (isDialogue) {
                        cleanText = part.removeSurrounding("“", "”").replace("“", "").replace("”", "")
                        
                        // 寻找原文中紧随对话后文的说话人标识，如“你好”张三说。
                        val afterTextIndex = s.indexOf(part) + part.length
                        if (afterTextIndex < s.length) {
                            val afterStr = s.substring(afterTextIndex)
                            val speakerName = speakerRegex.find(afterStr)?.groupValues?.get(1)
                            speaker = resolver.resolve(speakerName, fallbackVoices)
                        }
                    } else {
                        cleanText = part
                    }

                    tokens += Token(
                        id = md5ToLong(cleanText + index),
                        text = cleanText,
                        type = if (isDialogue) TokenType.DIALOGUE else TokenType.NARRATION,
                        speaker = speaker,
                        range = IntRange(index, index + part.length - 1)
                    )
                    index += part.length
                }
            }

            // Insert artificial pause at end of paragraph
            tokens += Token(
                id = md5ToLong("pause_$index"),
                text = "",
                type = TokenType.PAUSE,
                range = IntRange(index, index),
                durationHintMs = 400
            )

            index += 1 // newline character approx
        }

        return tokens
    }
}
