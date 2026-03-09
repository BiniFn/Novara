package org.skepsun.kototoro.reader.translate.domain

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.sentencepiece.SpTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxReaderTranslationEngine @Inject constructor(
	private val onnxModelManager: OnnxModelManager,
) {

	private sealed interface RuntimeHolder {
		val modelId: String
		fun close()
	}

	private data class GenericRuntime(
		override val modelId: String,
		val tokenizer: HuggingFaceTokenizer,
		val session: OrtSession,
		val inputNames: Set<String>,
		val logitsOutputName: String,
	) : RuntimeHolder {
		override fun close() {
			runCatching { session.close() }
			runCatching { tokenizer.close() }
		}
	}

	private data class NllbRuntime(
		override val modelId: String,
		val modelFamily: ModelFamily,
		val tokenizer: SpTokenizer,
		val encoderSession: OrtSession,
		val decoderSession: OrtSession,
		val cacheInitSession: OrtSession,
		val embedLmSession: OrtSession,
		val decoderInputNames: Set<String>,
		val decoderOutputNames: Set<String>,
		val layers: Int,
		val hiddenSize: Int,
	) : RuntimeHolder {
		override fun close() {
			runCatching { encoderSession.close() }
			runCatching { decoderSession.close() }
			runCatching { cacheInitSession.close() }
			runCatching { embedLmSession.close() }
			runCatching { tokenizer.close() }
		}
	}

	private data class Qwen35Runtime(
		override val modelId: String,
		val tokenizer: HuggingFaceTokenizer,
		val embedSession: OrtSession,
		val decoderSession: OrtSession,
		val decoderInputNames: Set<String>,
		val decoderInputInfo: Map<String, TensorInfo>,
		val stopTokenIds: Set<Long>,
	) : RuntimeHolder {
		override fun close() {
			runCatching { embedSession.close() }
			runCatching { decoderSession.close() }
			runCatching { tokenizer.close() }
		}
	}

	private data class QwenStepResult(
		val nextToken: Long,
		val decoderRun: OrtSession.Result,
		val totalSequenceLength: Int,
	)

	private val lock = Mutex()
	@Volatile
	private var runtime: RuntimeHolder? = null

	private enum class ModelFamily {
		NLLB,
		MADLAD_LIKE,
	}

	suspend fun translateBatch(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
		modelId: String,
	): Map<String, String> {
		if (texts.isEmpty()) return emptyMap()
		val rt = ensureRuntime(modelId) ?: return texts.associateWith { "" }
		return runInterruptible(Dispatchers.Default) {
			val map = LinkedHashMap<String, String>(texts.size)
			for (text in texts) {
				map[text] = runCatching {
						when (rt) {
							is NllbRuntime -> translateOneNllb(rt, text, sourceLang, targetLang)
							is GenericRuntime -> translateOneGeneric(rt, text, sourceLang, targetLang)
							is Qwen35Runtime -> translateOneQwen35(rt, text, sourceLang, targetLang)
						}
					}.onFailure { it.printStackTrace() }.getOrDefault("")
				}
			map
		}
	}

	private suspend fun ensureRuntime(modelId: String): RuntimeHolder? {
		if (modelId.isBlank() || !onnxModelManager.isModelDownloaded(modelId)) {
			return null
		}
		val current = runtime
		if (current != null && current.modelId == modelId) {
			return current
		}
		return lock.withLock {
			val again = runtime
			if (again != null && again.modelId == modelId) return@withLock again
			runtime?.close()
			val modelDir = onnxModelManager.getModelDir(modelId)
				val cacheRt = tryCreateNllbRuntime(modelId, modelDir) ?: tryCreateMadladLikeRuntime(modelId, modelDir)
				if (cacheRt != null) {
					runtime = cacheRt
					return@withLock cacheRt
				}
				val qwenRt = tryCreateQwen35Runtime(modelId, modelDir)
				if (qwenRt != null) {
					runtime = qwenRt
					return@withLock qwenRt
				}
				val genericRt = tryCreateGenericRuntime(modelId, modelDir)
				runtime = genericRt
				genericRt
			}
		}

	private fun tryCreateNllbRuntime(modelId: String, modelDir: File): NllbRuntime? {
		val encoder = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("NLLB_encoder.onnx", ignoreCase = true) }
		val decoder = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("NLLB_decoder.onnx", ignoreCase = true) }
		val cacheInit = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("NLLB_cache_initializer.onnx", ignoreCase = true) }
		val embedLm = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("NLLB_embed_and_lm_head.onnx", ignoreCase = true) }
		val spModel = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("sentencepiece_bpe.model", ignoreCase = true) }
		if (encoder == null || decoder == null || cacheInit == null || embedLm == null || spModel == null) {
			return null
		}
		val options = OrtSession.SessionOptions().apply {
			setMemoryPatternOptimization(false)
			setCPUArenaAllocator(false)
			setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
		}
		return try {
			val env = OrtEnvironment.getEnvironment()
			val decoderSession = env.createSession(decoder.absolutePath, options)
			NllbRuntime(
				modelId = modelId,
				modelFamily = ModelFamily.NLLB,
				tokenizer = SpTokenizer(spModel.toPath()),
				encoderSession = env.createSession(encoder.absolutePath, options),
				decoderSession = decoderSession,
				cacheInitSession = env.createSession(cacheInit.absolutePath, options),
				embedLmSession = env.createSession(embedLm.absolutePath, options),
				decoderInputNames = decoderSession.inputNames,
				decoderOutputNames = decoderSession.outputNames,
				layers = 12,
				hiddenSize = 64,
			)
		} catch (e: Throwable) {
			e.printStackTrace()
			null
		} finally {
			runCatching { options.close() }
		}
	}

	private fun tryCreateMadladLikeRuntime(modelId: String, modelDir: File): NllbRuntime? {
		if (!modelDir.exists()) return null
		val encoder = modelDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("onnx", true) && it.name.contains("encoder", true) }
		val decoder = modelDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("onnx", true) && it.name.contains("decoder", true) }
		val cacheInit = modelDir.walkTopDown().firstOrNull {
			it.isFile && it.extension.equals("onnx", true) &&
				it.name.contains("cache", true) && it.name.contains("init", true)
		}
		val embedLm = modelDir.walkTopDown().firstOrNull {
			it.isFile && it.extension.equals("onnx", true) &&
				(it.name.contains("embed", true) || it.name.contains("lm_head", true) || it.name.contains("lm", true))
		}
		val spModel = modelDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("model", true) }
		if (encoder == null || decoder == null || cacheInit == null || embedLm == null || spModel == null) {
			return null
		}
		val options = OrtSession.SessionOptions().apply {
			setMemoryPatternOptimization(false)
			setCPUArenaAllocator(false)
			setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
		}
		return try {
			val env = OrtEnvironment.getEnvironment()
			val decoderSession = env.createSession(decoder.absolutePath, options)
			NllbRuntime(
				modelId = modelId,
				modelFamily = ModelFamily.MADLAD_LIKE,
				tokenizer = SpTokenizer(spModel.toPath()),
				encoderSession = env.createSession(encoder.absolutePath, options),
				decoderSession = decoderSession,
				cacheInitSession = env.createSession(cacheInit.absolutePath, options),
				embedLmSession = env.createSession(embedLm.absolutePath, options),
				decoderInputNames = decoderSession.inputNames,
				decoderOutputNames = decoderSession.outputNames,
				layers = 32,
				hiddenSize = 128,
			)
		} catch (_: Throwable) {
			null
		} finally {
			runCatching { options.close() }
		}
	}

	private fun tryCreateGenericRuntime(modelId: String, modelDir: File): GenericRuntime? {
		val tokenizerPath = findTokenizerPath(modelDir) ?: return null
		val decoderPath = findDecoderOnnxPath(modelDir) ?: return null
		val tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath.toPath())
		val env = OrtEnvironment.getEnvironment()
		val options = OrtSession.SessionOptions().apply {
			setMemoryPatternOptimization(false)
			setCPUArenaAllocator(false)
			setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
		}
		val session = try {
			env.createSession(decoderPath.absolutePath, options)
		} finally {
			options.close()
		}
		val inputNames = session.inputNames
		if ("input_ids" !in inputNames) {
			runCatching { session.close() }
			runCatching { tokenizer.close() }
			return null
		}
		val logitsOutputName = session.outputNames.firstOrNull { it.contains("logits") }
			?: session.outputNames.firstOrNull()
			?: run {
				runCatching { session.close() }
				runCatching { tokenizer.close() }
				return null
			}
		return GenericRuntime(modelId, tokenizer, session, inputNames, logitsOutputName)
	}

	private fun tryCreateQwen35Runtime(modelId: String, modelDir: File): Qwen35Runtime? {
		val tokenizerPath = findTokenizerPath(modelDir) ?: return null
		val decoderPath = findQwenDecoderOnnxPath(modelDir) ?: return null
		val embedPath = findQwenEmbedOnnxPath(modelDir) ?: return null
		val tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath.toPath())
		val env = OrtEnvironment.getEnvironment()
		val options = OrtSession.SessionOptions().apply {
			setMemoryPatternOptimization(false)
			setCPUArenaAllocator(false)
			setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
		}
		return try {
			val embedSession = env.createSession(embedPath.absolutePath, options)
			val decoderSession = env.createSession(decoderPath.absolutePath, options)
			val inputNames = decoderSession.inputNames
			if ("inputs_embeds" !in inputNames) {
				runCatching { embedSession.close() }
				runCatching { decoderSession.close() }
				runCatching { tokenizer.close() }
				return null
			}
			val inputInfo = decoderSession.inputInfo.mapNotNull { (name, nodeInfo) ->
				(name to (nodeInfo.info as? TensorInfo))
			}.filter { it.second != null }.associate { it.first to it.second!! }
			Qwen35Runtime(
				modelId = modelId,
				tokenizer = tokenizer,
				embedSession = embedSession,
				decoderSession = decoderSession,
				decoderInputNames = inputNames,
				decoderInputInfo = inputInfo,
				stopTokenIds = loadStopTokens(modelDir),
			)
		} catch (_: Throwable) {
			runCatching { tokenizer.close() }
			null
		} finally {
			runCatching { options.close() }
		}
	}

	private fun translateOneGeneric(runtime: GenericRuntime, text: String, sourceLang: String, targetLang: String): String {
		val input = text.trim()
		if (input.isEmpty()) return ""
		val prompt = "Translate from $sourceLang to $targetLang. Return only translation.\\n\\n$input"
		val encoded = runtime.tokenizer.encode(prompt).ids
		if (encoded.isEmpty()) return ""
		val ids = encoded.toMutableList()
		val generated = ArrayList<Long>(MAX_NEW_TOKENS)
		repeat(MAX_NEW_TOKENS) {
			val next = runGenericDecoderStep(runtime, ids.toLongArray()) ?: return@repeat
			if (next in STOP_TOKEN_IDS) return@repeat
			generated += next
			ids += next
		}
		if (generated.isEmpty()) return ""
		return runtime.tokenizer.decode(generated.toLongArray()).trim()
	}

	private fun translateOneQwen35(
		runtime: Qwen35Runtime,
		text: String,
		sourceLang: String,
		targetLang: String,
	): String {
		val input = text.trim()
		if (input.isEmpty()) return ""
		val prompt = "Translate from $sourceLang to $targetLang. Return only the translated result.\n\n$input"
		val promptIds = runtime.tokenizer.encode(prompt).ids
		if (promptIds.isEmpty()) return ""
		val generated = ArrayList<Long>(MAX_NEW_TOKENS)
		var prevRun: OrtSession.Result? = null
		var nextInputIds = promptIds.map { it.toLong() }.toLongArray()
		var pastSequenceLength = 0
		var step = 0
		try {
			while (step < MAX_NEW_TOKENS) {
				val result = runQwen35Step(runtime, nextInputIds, pastSequenceLength, prevRun) ?: break
				runCatching { prevRun?.close() }
				prevRun = result.decoderRun
				pastSequenceLength = result.totalSequenceLength
				val nextToken = result.nextToken
				if (nextToken in runtime.stopTokenIds) {
					break
				}
				generated += nextToken
				nextInputIds = longArrayOf(nextToken)
				step++
			}
		} finally {
			runCatching { prevRun?.close() }
		}
		if (generated.isEmpty()) return ""
		return runtime.tokenizer.decode(generated.toLongArray()).trim()
	}

	private fun runQwen35Step(
		runtime: Qwen35Runtime,
		inputTokenIds: LongArray,
		pastSequenceLength: Int,
		prevRun: OrtSession.Result?,
	): QwenStepResult? {
		if (inputTokenIds.isEmpty()) return null
		val inputIdsTensor = createInt64Tensor(inputTokenIds)
		var embedRun: OrtSession.Result? = null
		val created = mutableListOf<OnnxTensor>()
		try {
			embedRun = runtime.embedSession.run(mapOf("input_ids" to inputIdsTensor))
			val embedTensor = (embedRun.get("inputs_embeds").orElse(null) as? OnnxTensor)
				?: (runtime.embedSession.outputNames.firstOrNull()?.let { name ->
					embedRun.get(name).orElse(null) as? OnnxTensor
				})
				?: return null
			val seqLen = inputTokenIds.size
			val totalLen = pastSequenceLength + seqLen
			val attentionMask = createInt64Tensor(LongArray(totalLen) { 1L }).also { created += it }
			val positionIds = createQwenPositionIds(pastSequenceLength, seqLen).also { created += it }
			val decoderInputs = mutableMapOf<String, OnnxTensor>()
			decoderInputs["inputs_embeds"] = embedTensor
			decoderInputs["attention_mask"] = attentionMask
			decoderInputs["position_ids"] = positionIds
			for (name in runtime.decoderInputNames) {
				if (name == "inputs_embeds" || name == "attention_mask" || name == "position_ids") continue
				if (name.startsWith("past_")) {
					if (prevRun == null) {
						val info = runtime.decoderInputInfo[name] ?: return null
						val zero = createZeroTensorForInput(name, info, pastSequenceLength = 0)
						decoderInputs[name] = zero
						created += zero
					} else {
						val presentName = mapPastInputToPresentOutput(name)
						decoderInputs[name] = prevRun.get(presentName).orElse(null) as? OnnxTensor ?: return null
					}
				} else {
					return null
				}
			}
			val decoderRun = runtime.decoderSession.run(decoderInputs)
			val logits = (decoderRun.get("logits").orElse(null) as? OnnxTensor)
				?: (runtime.decoderSession.outputNames.firstOrNull()?.let { name ->
					decoderRun.get(name).orElse(null) as? OnnxTensor
				})
				?: run {
					decoderRun.close()
					return null
				}
			val nextToken = argmaxLastToken(logits.value) ?: run {
				decoderRun.close()
				return null
			}
			return QwenStepResult(nextToken, decoderRun, totalLen)
		} finally {
			runCatching { inputIdsTensor.close() }
			created.forEach { runCatching { it.close() } }
			runCatching { embedRun?.close() }
		}
	}

	private fun runGenericDecoderStep(runtime: GenericRuntime, inputIds: LongArray): Long? {
		val inputs = linkedMapOf<String, OnnxTensor>()
		try {
			for (name in runtime.inputNames) {
				when (name) {
					"input_ids" -> inputs[name] = createInt64Tensor(inputIds)
					"attention_mask" -> inputs[name] = createInt64Tensor(LongArray(inputIds.size) { 1L })
					"position_ids" -> inputs[name] = createInt64Tensor(LongArray(inputIds.size) { it.toLong() })
					else -> return null
				}
			}
			runtime.session.run(inputs).use { result ->
				val logits = result.get(runtime.logitsOutputName).orElse(null) as? OnnxTensor ?: return null
				return argmaxLastToken(logits.value)
			}
		} finally {
			inputs.values.forEach { runCatching { it.close() } }
		}
	}

	private fun translateOneNllb(runtime: NllbRuntime, text: String, sourceLang: String, targetLang: String): String {
		val input = text.trim()
		if (input.isEmpty()) return ""
		val sp = runtime.tokenizer.getProcessor()
		val eos = sp.getId("</s>")
		val srcNllb = toNllbCode(sourceLang)
		val tgtNllb = toNllbCode(targetLang)
		val tgtLangId = tgtNllb?.let(::languageId)
		val encoded = when (runtime.modelFamily) {
			ModelFamily.NLLB -> sp.encode(input)
			ModelFamily.MADLAD_LIKE -> sp.encode("<2${targetLang.trim().lowercase().substringBefore('-')}> $input")
		}
		val inputIds = when (runtime.modelFamily) {
			ModelFamily.NLLB -> {
				val srcLangId = srcNllb?.let(::languageId) ?: return ""
				val ids = IntArray(encoded.size + 2)
				ids[0] = srcLangId
				for (i in encoded.indices) {
					ids[i + 1] = spToNllbToken(encoded[i])
				}
				ids[ids.lastIndex] = eos
				ids
			}
			ModelFamily.MADLAD_LIKE -> {
				val ids = IntArray(encoded.size + 1)
				for (i in encoded.indices) {
					ids[i] = encoded[i]
				}
				ids[ids.lastIndex] = eos
				ids
			}
		}
		val attentionMask = IntArray(inputIds.size) { 1 }

		var encoderRun: OrtSession.Result? = null
		var cacheInitRun: OrtSession.Result? = null
			var prevDecoderRun: OrtSession.Result? = null
			var emptyPast: OnnxTensor? = null
			try {
				val encoderInput = mutableMapOf<String, OnnxTensor>()
				val encoderCreated = mutableListOf<OnnxTensor>()
				var encoderEmbedResult: OrtSession.Result? = null
				val inputIdsTensor = createInt64Tensor(inputIds.map { it.toLong() }.toLongArray()).also { encoderCreated += it }
				val attTensor = createInt64Tensor(attentionMask.map { it.toLong() }.toLongArray()).also { encoderCreated += it }
				if ("input_ids" in runtime.encoderSession.inputNames) encoderInput["input_ids"] = inputIdsTensor
				if ("attention_mask" in runtime.encoderSession.inputNames) encoderInput["attention_mask"] = attTensor
				if ("embed_matrix" in runtime.encoderSession.inputNames) {
					val embed = runNllbEmbed(runtime, inputIdsTensor, false, null) ?: return ""
					encoderInput["embed_matrix"] = embed.first
					encoderEmbedResult = embed.second
				}
				encoderRun = runtime.encoderSession.run(encoderInput)
				runCatching { encoderEmbedResult?.close() }
				encoderCreated.forEach { runCatching { it.close() } }
				val encoderHidden = encoderRun.get("last_hidden_state").orElse(null) as? OnnxTensor ?: return ""

			val cacheInput = mutableMapOf<String, OnnxTensor>()
			cacheInput["encoder_hidden_states"] = encoderHidden
			cacheInitRun = runtime.cacheInitSession.run(cacheInput)
			emptyPast = createEmptyPastTensor(runtime.hiddenSize)

			val outputTokens = mutableListOf<Int>()
			var step = 0
			var nextInputId = if (runtime.modelFamily == ModelFamily.NLLB) NLLB_DECODER_START_TOKEN else MADLAD_DECODER_START_TOKEN
			while (step < MAX_NEW_TOKENS) {
				val decInputTensors = mutableMapOf<String, OnnxTensor>()
				val created = mutableListOf<OnnxTensor>()
				val inIds = createInt64Tensor(longArrayOf(nextInputId.toLong())).also { created += it }
				val encMask = createInt64Tensor(attentionMask.map { it.toLong() }.toLongArray()).also { created += it }
				if ("input_ids" in runtime.decoderInputNames) decInputTensors["input_ids"] = inIds
				if ("encoder_attention_mask" in runtime.decoderInputNames) decInputTensors["encoder_attention_mask"] = encMask
				if ("encoder_hidden_states" in runtime.decoderInputNames) decInputTensors["encoder_hidden_states"] = encoderHidden
				if ("embed_matrix" in runtime.decoderInputNames) {
					val embed = runNllbEmbed(runtime, inIds, false, null) ?: return ""
					decInputTensors["embed_matrix"] = embed.first
					runCatching { embed.second.close() }
				}
				for (i in 0 until runtime.layers) {
					val dKeyName = "past_key_values.$i.decoder.key"
					val dValName = "past_key_values.$i.decoder.value"
					val eKeyName = "past_key_values.$i.encoder.key"
					val eValName = "past_key_values.$i.encoder.value"
					if (dKeyName in runtime.decoderInputNames) {
						decInputTensors[dKeyName] = if (step == 0) emptyPast!! else (prevDecoderRun?.get("present.$i.decoder.key")?.orElse(null) as? OnnxTensor ?: return "")
					}
					if (dValName in runtime.decoderInputNames) {
						decInputTensors[dValName] = if (step == 0) emptyPast!! else (prevDecoderRun?.get("present.$i.decoder.value")?.orElse(null) as? OnnxTensor ?: return "")
					}
					if (eKeyName in runtime.decoderInputNames) {
						decInputTensors[eKeyName] = cacheInitRun?.get("present.$i.encoder.key")?.orElse(null) as? OnnxTensor ?: return ""
					}
					if (eValName in runtime.decoderInputNames) {
						decInputTensors[eValName] = cacheInitRun?.get("present.$i.encoder.value")?.orElse(null) as? OnnxTensor ?: return ""
					}
				}
				val decoderRun = runtime.decoderSession.run(decInputTensors)
				created.forEach { runCatching { it.close() } }
				val token = selectNllbNextToken(runtime, decoderRun) ?: run {
					decoderRun.close()
					return ""
				}
				prevDecoderRun?.close()
				prevDecoderRun = decoderRun
				if (token == eos) {
					break
				}
				outputTokens += token
				nextInputId = if (runtime.modelFamily == ModelFamily.NLLB && step == 0) {
					tgtLangId ?: return ""
				} else {
					token
				}
				step++
			}
			if (outputTokens.isEmpty()) return ""
			val spIds = when (runtime.modelFamily) {
				ModelFamily.NLLB -> outputTokens.mapNotNull { nllbToSpToken(it) }.toIntArray()
				ModelFamily.MADLAD_LIKE -> outputTokens.filter { it in 4 until NLLB_DICTIONARY_LENGTH }.toIntArray()
			}
			if (spIds.isEmpty()) return ""
			return sp.decode(spIds).replace('▁', ' ').trim()
		} finally {
			runCatching { prevDecoderRun?.close() }
			runCatching { cacheInitRun?.close() }
			runCatching { encoderRun?.close() }
			runCatching { emptyPast?.close() }
		}
	}

	private fun selectNllbNextToken(runtime: NllbRuntime, decoderRun: OrtSession.Result): Int? {
		val logitsTensor = if ("logits" in runtime.decoderOutputNames) {
			decoderRun.get("logits").orElse(null) as? OnnxTensor
		} else {
			val preLogits = decoderRun.get("pre_logits").orElse(null) as? OnnxTensor ?: return null
			val lm = runNllbEmbed(runtime, null, true, preLogits) ?: return null
			val result = lm.second
			val logits = result.get("logits").orElse(null) as? OnnxTensor ?: run {
				result.close()
				return null
			}
			val token = argmaxLastToken(logits.value)?.toInt()
			result.close()
			return token
		}
		val raw = logitsTensor?.value ?: return null
		return argmaxLastToken(raw)?.toInt()
	}

	private fun runNllbEmbed(
		runtime: NllbRuntime,
		inputIds: OnnxTensor?,
		useLmHead: Boolean,
		preLogits: OnnxTensor?,
	): Pair<OnnxTensor, OrtSession.Result>? {
		val inputs = mutableMapOf<String, OnnxTensor>()
		val created = mutableListOf<OnnxTensor>()
		try {
			if (inputIds != null && "input_ids" in runtime.embedLmSession.inputNames) {
				inputs["input_ids"] = inputIds
			} else if ("input_ids" in runtime.embedLmSession.inputNames) {
				createInt64Tensor(longArrayOf(0L, 2L)).also {
					created += it
					inputs["input_ids"] = it
				}
			}
			if ("pre_logits" in runtime.embedLmSession.inputNames) {
				if (preLogits != null) {
					inputs["pre_logits"] = preLogits
				} else {
					createFloatTensor(longArrayOf(0L, 1L, 1024L)).also {
						created += it
						inputs["pre_logits"] = it
					}
				}
			}
			if ("use_lm_head" in runtime.embedLmSession.inputNames) {
				createBooleanTensor(useLmHead).also {
					created += it
					inputs["use_lm_head"] = it
				}
			}
			val result = runtime.embedLmSession.run(inputs)
			val outputName = if (useLmHead) "logits" else "embed_matrix"
			val tensor = result.get(outputName).orElse(null) as? OnnxTensor ?: run {
				result.close()
				return null
			}
			return tensor to result
		} finally {
			created.forEach { runCatching { it.close() } }
		}
	}

	private fun createInt64Tensor(values: LongArray): OnnxTensor {
		return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(values), longArrayOf(1L, values.size.toLong()))
	}

	private fun createQwenPositionIds(pastSequenceLength: Int, sequenceLength: Int): OnnxTensor {
		val values = LongArray(3 * sequenceLength)
		for (i in 0 until sequenceLength) {
			val pos = (pastSequenceLength + i).toLong()
			values[i] = pos
			values[sequenceLength + i] = pos
			values[sequenceLength * 2 + i] = pos
		}
		return OnnxTensor.createTensor(
			OrtEnvironment.getEnvironment(),
			LongBuffer.wrap(values),
			longArrayOf(3L, 1L, sequenceLength.toLong()),
		)
	}

	private fun createZeroTensorForInput(name: String, info: TensorInfo, pastSequenceLength: Int): OnnxTensor {
		val shape = info.shape.mapIndexed { index, dim ->
			when {
				dim > 0L -> dim
				index == 0 -> 1L
				name.startsWith("past_key_values.") && index == 2 -> pastSequenceLength.toLong()
				else -> 1L
			}
		}.toLongArray()
		val size = shape.fold(1L) { acc, dim -> acc * dim }.toInt().coerceAtLeast(0)
		return OnnxTensor.createTensor(
			OrtEnvironment.getEnvironment(),
			FloatBuffer.wrap(FloatArray(size)),
			shape,
		)
	}

	private fun mapPastInputToPresentOutput(name: String): String {
		return when {
			name.startsWith("past_key_values.") -> name.replaceFirst("past_key_values.", "present.")
			name.startsWith("past_conv.") -> name.replaceFirst("past_conv.", "present_conv.")
			name.startsWith("past_recurrent.") -> name.replaceFirst("past_recurrent.", "present_recurrent.")
			else -> name
		}
	}

	private fun createBooleanTensor(value: Boolean): OnnxTensor {
		return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), booleanArrayOf(value))
	}

	private fun createFloatTensor(shape: LongArray): OnnxTensor {
		val size = shape.fold(1L) { acc, dim -> acc * dim }.toInt().coerceAtLeast(0)
		return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(FloatArray(size) { 0f }), shape)
	}

	private fun createEmptyPastTensor(hiddenSize: Int): OnnxTensor {
		return OnnxTensor.createTensor(
			OrtEnvironment.getEnvironment(),
			FloatBuffer.wrap(FloatArray(0)),
			longArrayOf(1L, 16L, 0L, hiddenSize.toLong()),
		)
	}

	private fun argmaxLastToken(value: Any): Long? {
		return when (value) {
			is Array<*> -> argmaxFromArray(value)
			else -> null
		}
	}

	private fun argmaxFromArray(value: Array<*>): Long? {
		if (value.isEmpty()) return null
		val first = value.firstOrNull() ?: return null
		if (first is FloatArray) return argmax(first)
		if (first is Array<*>) {
			val lastSeq = first.lastOrNull()
			if (lastSeq is FloatArray) return argmax(lastSeq)
			if (lastSeq is Array<*>) {
				val last = lastSeq.lastOrNull()
				if (last is FloatArray) return argmax(last)
			}
		}
		return null
	}

	private fun argmax(values: FloatArray): Long {
		var maxIndex = 0
		var maxValue = Float.NEGATIVE_INFINITY
		for (i in values.indices) {
			if (values[i] > maxValue) {
				maxValue = values[i]
				maxIndex = i
			}
		}
		return maxIndex.toLong()
	}

	private fun findTokenizerPath(modelDir: File): File? {
		if (!modelDir.exists()) return null
		val tokenizerJson = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("tokenizer.json", ignoreCase = true) }
		if (tokenizerJson != null) return tokenizerJson.parentFile
		val vocab = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("vocab.json", ignoreCase = true) }
		val merges = modelDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("merges.txt", ignoreCase = true) }
		return if (vocab != null && merges != null && vocab.parentFile == merges.parentFile) vocab.parentFile else null
	}

	private fun findDecoderOnnxPath(modelDir: File): File? {
		if (!modelDir.exists()) return null
		return modelDir.walkTopDown()
			.filter { it.isFile && it.extension.equals("onnx", ignoreCase = true) }
			.sortedWith(compareByDescending<File> { it.name.contains("decoder", true) }.thenByDescending { it.length() })
			.firstOrNull()
	}

	private fun findQwenDecoderOnnxPath(modelDir: File): File? {
		if (!modelDir.exists()) return null
		return modelDir.walkTopDown()
			.filter { it.isFile && it.extension.equals("onnx", true) && it.name.contains("decoder_model_merged", true) }
			.sortedWith(compareByDescending<File> { it.name.contains("_q4", true) }.thenByDescending { it.length() })
			.firstOrNull()
	}

	private fun findQwenEmbedOnnxPath(modelDir: File): File? {
		if (!modelDir.exists()) return null
		return modelDir.walkTopDown()
			.filter { it.isFile && it.extension.equals("onnx", true) && it.name.contains("embed_tokens", true) }
			.sortedWith(compareByDescending<File> { it.name.contains("_q4", true) }.thenByDescending { it.length() })
			.firstOrNull()
	}

	private fun loadStopTokens(modelDir: File): Set<Long> {
		val defaults = mutableSetOf(0L, 1L, 2L, QWEN_EOS_TOKEN_ID, QWEN_ALT_EOS_TOKEN_ID)
		val generationConfig = File(modelDir, "generation_config.json")
		if (!generationConfig.isFile) return defaults
		return runCatching {
			val root = JSONObject(generationConfig.readText())
			when (val eos = root.opt("eos_token_id")) {
				is Number -> defaults += eos.toLong()
				is JSONArray -> {
					for (i in 0 until eos.length()) {
						val value = eos.opt(i)
						if (value is Number) defaults += value.toLong()
					}
				}
			}
			defaults
		}.getOrDefault(defaults)
	}

	private fun toNllbCode(lang: String): String? {
		val raw = lang.trim().lowercase()
		val key = raw.substringBefore('-')
		return BCP47_TO_NLLB[key]
	}

	private fun languageId(nllbCode: String): Int? {
		val index = NLLB_LANGUAGE_LIST.indexOf(nllbCode)
		if (index < 0) return null
		return NLLB_DICTIONARY_LENGTH + index + 1
	}

	private fun spToNllbToken(spId: Int): Int {
		val shifted = spId + 1
		return when (shifted) {
			1 -> 3
			2 -> 0
			3 -> 2
			else -> shifted
		}
	}

	private fun nllbToSpToken(nllbId: Int): Int? {
		if (nllbId >= NLLB_DICTIONARY_LENGTH || nllbId <= 3) return null
		return nllbId - 1
	}

	companion object {
		private const val MAX_NEW_TOKENS = 200
		private const val NLLB_DICTIONARY_LENGTH = 256000
			private const val NLLB_DECODER_START_TOKEN = 2
			private const val MADLAD_DECODER_START_TOKEN = 0
			private const val QWEN_EOS_TOKEN_ID = 248044L
			private const val QWEN_ALT_EOS_TOKEN_ID = 248046L
			private val STOP_TOKEN_IDS = setOf(0L, 1L, 2L)

		private val BCP47_TO_NLLB = mapOf(
			"ar" to "arb_Arab", "bg" to "bul_Cyrl", "ca" to "cat_Latn", "zh" to "zho_Hans",
			"cs" to "ces_Latn", "da" to "dan_Latn", "de" to "deu_Latn", "el" to "ell_Grek",
			"en" to "eng_Latn", "es" to "spa_Latn", "fi" to "fin_Latn", "fr" to "fra_Latn",
			"gl" to "glg_Latn", "hr" to "hrv_Latn", "it" to "ita_Latn", "ja" to "jpn_Jpan",
			"ko" to "kor_Hang", "mk" to "mkd_Cyrl", "nl" to "nld_Latn", "pl" to "pol_Latn",
			"pt" to "por_Latn", "ro" to "ron_Latn", "ru" to "rus_Cyrl", "sk" to "slk_Latn",
			"sv" to "swe_Latn", "tl" to "tgl_Latn", "tr" to "tur_Latn", "uk" to "ukr_Cyrl",
			"vi" to "vie_Latn",
		)

		private val NLLB_LANGUAGE_LIST = listOf(
			"ace_Arab", "ace_Latn", "acm_Arab", "acq_Arab", "aeb_Arab", "afr_Latn", "ajp_Arab", "aka_Latn", "amh_Ethi", "apc_Arab", "arb_Arab", "ars_Arab", "ary_Arab", "arz_Arab", "asm_Beng", "ast_Latn", "awa_Deva", "ayr_Latn", "azb_Arab", "azj_Latn", "bak_Cyrl", "bam_Latn", "ban_Latn", "bel_Cyrl", "bem_Latn", "ben_Beng", "bho_Deva", "bjn_Arab", "bjn_Latn", "bod_Tibt", "bos_Latn", "bug_Latn", "bul_Cyrl", "cat_Latn", "ceb_Latn", "ces_Latn", "cjk_Latn", "ckb_Arab", "crh_Latn", "cym_Latn", "dan_Latn", "deu_Latn", "dik_Latn", "dyu_Latn", "dzo_Tibt", "ell_Grek", "eng_Latn", "epo_Latn", "est_Latn", "eus_Latn", "ewe_Latn", "fao_Latn", "pes_Arab", "fij_Latn", "fin_Latn", "fon_Latn", "fra_Latn", "fur_Latn", "fuv_Latn", "gla_Latn", "gle_Latn", "glg_Latn", "grn_Latn", "guj_Gujr", "hat_Latn", "hau_Latn", "heb_Hebr", "hin_Deva", "hne_Deva", "hrv_Latn", "hun_Latn", "hye_Armn", "ibo_Latn", "ilo_Latn", "ind_Latn", "isl_Latn", "ita_Latn", "jav_Latn", "jpn_Jpan", "kab_Latn", "kac_Latn", "kam_Latn", "kan_Knda", "kas_Arab", "kas_Deva", "kat_Geor", "knc_Arab", "knc_Latn", "kaz_Cyrl", "kbp_Latn", "kea_Latn", "khm_Khmr", "kik_Latn", "kin_Latn", "kir_Cyrl", "kmb_Latn", "kon_Latn", "kor_Hang", "kmr_Latn", "lao_Laoo", "lvs_Latn", "lij_Latn", "lim_Latn", "lin_Latn", "lit_Latn", "lmo_Latn", "ltg_Latn", "ltz_Latn", "lua_Latn", "lug_Latn", "luo_Latn", "lus_Latn", "mag_Deva", "mai_Deva", "mal_Mlym", "mar_Deva", "min_Latn", "mkd_Cyrl", "plt_Latn", "mlt_Latn", "mni_Beng", "khk_Cyrl", "mos_Latn", "mri_Latn", "zsm_Latn", "mya_Mymr", "nld_Latn", "nno_Latn", "nob_Latn", "npi_Deva", "nso_Latn", "nus_Latn", "nya_Latn", "oci_Latn", "gaz_Latn", "ory_Orya", "pag_Latn", "pan_Guru", "pap_Latn", "pol_Latn", "por_Latn", "prs_Arab", "pbt_Arab", "quy_Latn", "ron_Latn", "run_Latn", "rus_Cyrl", "sag_Latn", "san_Deva", "sat_Beng", "scn_Latn", "shn_Mymr", "sin_Sinh", "slk_Latn", "slv_Latn", "smo_Latn", "sna_Latn", "snd_Arab", "som_Latn", "sot_Latn", "spa_Latn", "als_Latn", "srd_Latn", "srp_Cyrl", "ssw_Latn", "sun_Latn", "swe_Latn", "swh_Latn", "szl_Latn", "tam_Taml", "tat_Cyrl", "tel_Telu", "tgk_Cyrl", "tgl_Latn", "tha_Thai", "tir_Ethi", "taq_Latn", "taq_Tfng", "tpi_Latn", "tsn_Latn", "tso_Latn", "tuk_Latn", "tum_Latn", "tur_Latn", "twi_Latn", "tzm_Tfng", "uig_Arab", "ukr_Cyrl", "umb_Latn", "urd_Arab", "uzn_Latn", "vec_Latn", "vie_Latn", "war_Latn", "wol_Latn", "xho_Latn", "ydd_Hebr", "yor_Latn", "yue_Hant", "zho_Hans", "zho_Hant", "zul_Latn"
		)
	}
}
