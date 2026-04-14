package org.skepsun.kototoro.reader.novel

/**
 * 将 NovelContentLoader 输出的纯文本切分为段落列表。
 *
 * 切分规则：
 * - 以 \n\n（两个连续换行）作为段落边界
 * - 以 "📷 [图片:" 开头的行识别为 IMAGE 类型，跳过翻译
 * - 过短（< MIN_TEXT_LENGTH 字符）或纯空白的段落也跳过翻译
 * - 段落内的单换行（\n）保留，不拆分
 */
object NovelParagraphSplitter {

    private const val MIN_TEXT_LENGTH = 4
    private val IMAGE_PREFIX_PATTERN = Regex("""^📷\s*\[图片:""")

    /**
     * 将章节内容切分为段落列表。
     */
    fun split(content: String): List<NovelParagraph> {
        if (content.isBlank()) return emptyList()

        // 先将 3 个及以上连续换行规范化为 2 个，保证分段一致性
        val normalized = content.replace(Regex("\\n{3,}"), "\n\n")
        val rawParagraphs = normalized.split("\n\n")

        val result = mutableListOf<NovelParagraph>()
        for ((i, raw) in rawParagraphs.withIndex()) {
            val trimmed = raw.trim()
            val type = when {
                IMAGE_PREFIX_PATTERN.containsMatchIn(trimmed) -> NovelParagraphType.IMAGE
                trimmed.length < MIN_TEXT_LENGTH -> NovelParagraphType.IMAGE // 太短，视为跳过
                else -> NovelParagraphType.TEXT
            }
            result.add(
                NovelParagraph(
                    index = i,
                    type = type,
                    originalText = trimmed,
                )
            )
        }
        return result
    }

    /**
     * 从段落列表中提取需要翻译的文本（TEXT 类型且非空）。
     * 返回的列表顺序与 paragraphs 中 TEXT 段落顺序一致。
     */
    fun buildTranslationInput(paragraphs: List<NovelParagraph>): List<String> {
        return paragraphs
            .filter { it.type == NovelParagraphType.TEXT && it.originalText.isNotBlank() }
            .map { it.originalText }
    }
}
