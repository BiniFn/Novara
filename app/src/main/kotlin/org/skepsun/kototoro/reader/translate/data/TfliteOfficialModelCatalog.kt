package org.skepsun.kototoro.reader.translate.data

data class TfliteOfficialModel(
    val id: String,
    val title: String,
    val version: String,
    val encoderUrl: String,
    val decoderUrl: String,
    val vocabUrl: String,
    val embeddingsUrl: String? = null,
)

object TfliteOfficialModelCatalog {
    private const val BASE_URL = "https://hf-mirror.com/bluolightning/manga-ocr-tflite/resolve/main/"

    val models = listOf(
        TfliteOfficialModel(
            id = "mangaocr_2025_fp16",
            title = "MangaOCR 2025 (Float16)",
            version = "2025.01",
            encoderUrl = BASE_URL + "mocr_2025_encoder_float16.tflite",
            decoderUrl = BASE_URL + "mocr_2025_decoder_float16.tflite",
            vocabUrl = BASE_URL + "vocab.csv"
        ),
        TfliteOfficialModel(
            id = "mangaocr_2025_fp32",
            title = "MangaOCR 2025 (Float32/GPU)",
            version = "2025.01-fp32",
            encoderUrl = BASE_URL + "mocr_2025_encoder_fp32.tflite",
            decoderUrl = BASE_URL + "mocr_2025_decoder_float32.tflite",
            vocabUrl = BASE_URL + "vocab.csv",
            embeddingsUrl = BASE_URL + "mocr_2025_embeddings_float32.bin"
        ),
        TfliteOfficialModel(
            id = "mangaocr_legacy",
            title = "MangaOCR Legacy",
            version = "v1.0",
            encoderUrl = BASE_URL + "encoder_float16.tflite",
            decoderUrl = BASE_URL + "decoder_float16.tflite",
            vocabUrl = BASE_URL + "vocab.csv"
        )
    )

    fun findById(id: String?): TfliteOfficialModel? {
        return models.firstOrNull { it.id == id }
    }
}
