package org.skepsun.kototoro.reader.translate.data

data class TfliteOfficialModel(
    val id: String,
    val title: String,
    val version: String,
    val encoderUrl: String,
    val decoderUrl: String,
    val vocabUrl: String? = null,
    val embeddingsUrl: String? = null,
)

object TfliteOfficialModelCatalog {
    private const val YOMIHON_HF_BASE = "https://huggingface.co/bluolightning/"

    val models = listOf(
        TfliteOfficialModel(
            id = "yomihon_fast_v1_fp16",
            title = "ContentOCR Fast v1 fp16 (Yomihon Compatible)",
            version = "yomihon_fast_v1_fp16",
            encoderUrl = YOMIHON_HF_BASE + "manga-ocr-mobile/resolve/main/v1_fp16/encoder.tflite?download=true",
            decoderUrl = YOMIHON_HF_BASE + "manga-ocr-mobile/resolve/main/v1_fp16/decoder.tflite?download=true",
        )
    )

    fun findById(id: String?): TfliteOfficialModel? {
        return models.firstOrNull { it.id == id }
    }
}
