package org.skepsun.kototoro.reader.translate.data

data class OnnxOfficialModel(
	val id: String,
	val title: String,
	val version: String,
	val category: OnnxModelCategory = OnnxModelCategory.CLASSIC_TRANSLATION,
	val archiveUrl: String? = null,
	val sha256: String? = null,
	val files: List<OnnxModelFile> = emptyList(),
	val description: String,
)

enum class OnnxModelCategory {
	CLASSIC_TRANSLATION,
	GENERAL_LLM,
	BUBBLE_DETECTION,
}

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
		OnnxOfficialModel(
			id = "qwen3_5_0_8b_onnx_q4",
			title = "Qwen3.5-0.8B ONNX (q4)",
			version = "onnx-community",
			category = OnnxModelCategory.GENERAL_LLM,
			files = listOf(
				OnnxModelFile(
					fileName = "decoder_model_merged_q4.onnx",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/onnx/decoder_model_merged_q4.onnx",
				),
				OnnxModelFile(
					fileName = "decoder_model_merged_q4.onnx_data",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/onnx/decoder_model_merged_q4.onnx_data",
				),
				OnnxModelFile(
					fileName = "embed_tokens_q4.onnx",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/onnx/embed_tokens_q4.onnx",
				),
				OnnxModelFile(
					fileName = "embed_tokens_q4.onnx_data",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/onnx/embed_tokens_q4.onnx_data",
				),
				OnnxModelFile(
					fileName = "tokenizer.json",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/tokenizer.json",
				),
				OnnxModelFile(
					fileName = "tokenizer_config.json",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/tokenizer_config.json",
				),
				OnnxModelFile(
					fileName = "config.json",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/config.json",
				),
				OnnxModelFile(
					fileName = "generation_config.json",
					downloadUrl = "https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX/resolve/main/generation_config.json",
				),
			),
			description = "General small LLM for translation fallback, based on Qwen3.5-0.8B ONNX q4.",
		),
		OnnxOfficialModel(
			id = "manga_bubble_yolo_hf_main",
			title = "Content Bubble YOLO",
			version = "hf-main",
			category = OnnxModelCategory.BUBBLE_DETECTION,
			files = listOf(
				OnnxModelFile(
					fileName = "yolo26s.onnx",
					downloadUrl = "https://huggingface.co/Kiuyha/Content-Bubble-YOLO/resolve/main/onnx/yolo26s.onnx",
				),
			),
			description = "YOLO-based manga bubble/text region detector for ROI OCR pipeline.",
		),
		OnnxOfficialModel(
			id = "translategemma_4b_it_onnx_int4",
			title = "TranslateGemma 4B ONNX (q4)",
			version = "int4",
			category = OnnxModelCategory.GENERAL_LLM,
			files = listOf(
				OnnxModelFile(
					fileName = "chat_template.jinja",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/chat_template.jinja",
				),
				OnnxModelFile(
					fileName = "config.json",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/config.json",
				),
				OnnxModelFile(
					fileName = "genai_config.json",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/genai_config.json",
				),
				OnnxModelFile(
					fileName = "model.onnx",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/model.onnx",
				),
				OnnxModelFile(
					fileName = "model.onnx.data",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/model.onnx.data",
				),
				OnnxModelFile(
					fileName = "special_tokens_map.json",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/special_tokens_map.json",
				),
				OnnxModelFile(
					fileName = "tokenizer.json",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/tokenizer.json",
				),
				OnnxModelFile(
					fileName = "tokenizer_config.json",
					downloadUrl = "https://huggingface.co/keisuke-miyako/translategemma-4b-it-onnx-int4/resolve/main/tokenizer_config.json",
				),
			),
			description = "TranslateGemma 4B IT ONNX Int4. Supports text translation and direct image translation (vision-language).",
		),
	)

	fun findById(id: String?): OnnxOfficialModel? {
		return models.firstOrNull { it.id == id }
	}
}
