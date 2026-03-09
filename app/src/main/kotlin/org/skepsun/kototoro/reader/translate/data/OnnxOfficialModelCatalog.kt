package org.skepsun.kototoro.reader.translate.data

data class OnnxOfficialModel(
	val id: String,
	val title: String,
	val version: String,
	val archiveUrl: String? = null,
	val sha256: String? = null,
	val files: List<OnnxModelFile> = emptyList(),
	val description: String,
)

data class OnnxModelFile(
	val fileName: String,
	val downloadUrl: String,
	val sha256: String? = null,
)

object OnnxOfficialModelCatalog {
	const val source = "https://github.com/niedev/OnnxModelsEnhancer/releases, https://github.com/niedev/RTranslator/releases"

	val models = listOf(
		OnnxOfficialModel(
			id = "nllb_rtranslator_2_0_0",
			title = "NLLB (RTranslator v2.0.0)",
			version = "2.0.0",
			files = listOf(
				OnnxModelFile(
					fileName = "NLLB_cache_initializer.onnx",
					downloadUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/NLLB_cache_initializer.onnx",
				),
				OnnxModelFile(
					fileName = "NLLB_decoder.onnx",
					downloadUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/NLLB_decoder.onnx",
				),
				OnnxModelFile(
					fileName = "NLLB_embed_and_lm_head.onnx",
					downloadUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/NLLB_embed_and_lm_head.onnx",
				),
				OnnxModelFile(
					fileName = "NLLB_encoder.onnx",
					downloadUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/NLLB_encoder.onnx",
				),
				OnnxModelFile(
					fileName = "sentencepiece_bpe.model",
					downloadUrl = "https://raw.githubusercontent.com/niedev/RTranslator/v2.00/app/src/main/assets/sentencepiece_bpe.model",
				),
			),
			description = "RTranslator-compatible NLLB cache architecture (encoder + decoder + cache init + embed/lm).",
		),
		OnnxOfficialModel(
			id = "hy_mt_v1_0_0_beta",
			title = "HY-MT 1.5 (OnnxModelsEnhancer)",
			version = "v1.0.0-beta",
			archiveUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/HY-MT.zip",
			sha256 = "52109bad6bb72a5a0c1cf66a62547495f024b795864f8d380bcf46be7977d517",
			description = "Decoder-only large local translation model optimized for ONNX Runtime.",
		),
		OnnxOfficialModel(
			id = "madlad_v1_0_0_beta",
			title = "Madlad 400 (OnnxModelsEnhancer)",
			version = "v1.0.0-beta",
			archiveUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/Madlad.zip",
			sha256 = "ec3183e6106182938fe095d8e48057e2412ded278b560c2dc818c72cfedd682d",
			description = "Large multilingual translation model with optimized cache architecture.",
		),
		OnnxOfficialModel(
			id = "mozilla_v1_0_0_beta",
			title = "Mozilla (OnnxModelsEnhancer)",
			version = "v1.0.0-beta",
			archiveUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/Mozilla.zip",
			sha256 = "05e7187ff86e267e559d3b126efeb6152d1107e6650ffc56aa6cae05a54e674e",
			description = "Mozilla translation model pack mirrored for RTranslator compatibility.",
		),
	)

	fun findById(id: String?): OnnxOfficialModel? {
		return models.firstOrNull { it.id == id }
	}
}
