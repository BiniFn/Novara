package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureNanoTime

/**
 * OCR engine for the fast model.
 * Optimized for ARM CPU with KV-cache based decoding.
 */
class FastOcrEngine(
    private val encoderModelPath: String,
    private val decoderModelPath: String,
    private val environment: Environment,
    private val textPostprocessor: TextPostprocessor,
) {


    private lateinit var encoderModel: CompiledModel
    private lateinit var decoderModel: CompiledModel

    // Encoder buffers
    private lateinit var encoderImageInput: TensorBuffer
    private lateinit var encoderHiddenStatesOutput: TensorBuffer

    // Decoder init signature buffers
    private lateinit var initEncoderStatesInput: TensorBuffer
    private lateinit var initInputIdsInput: TensorBuffer
    private lateinit var initLogitsOutput: TensorBuffer
    private lateinit var initSelfKSliceOutput: TensorBuffer
    private lateinit var initSelfVSliceOutput: TensorBuffer
    private lateinit var initCrossKOutput: TensorBuffer
    private lateinit var initCrossVOutput: TensorBuffer

    // Decoder step signature buffers
    private lateinit var stepEncoderStatesInput: TensorBuffer
    private lateinit var stepInputIdsInput: TensorBuffer
    private lateinit var stepPositionIdsInput: TensorBuffer
    private lateinit var stepSelfKCacheInput: TensorBuffer
    private lateinit var stepSelfVCacheInput: TensorBuffer
    private lateinit var stepCrossKInput: TensorBuffer
    private lateinit var stepCrossVInput: TensorBuffer
    private lateinit var stepLogitsOutput: TensorBuffer
    private lateinit var stepSelfKSliceOutput: TensorBuffer
    private lateinit var stepSelfVSliceOutput: TensorBuffer

    // Preprocessing scratch
    private lateinit var scratchBitmap: Bitmap
    private lateinit var scratchCanvas: Canvas
    private lateinit var scratchPaint: Paint
    private val dstRect = Rect()

    // Pre-allocated buffers
    private val pixelsBuffer = IntArray(IMAGE_SIZE * IMAGE_SIZE)
    private val nchwBuffer = FloatArray(IMAGE_SIZE * IMAGE_SIZE * 3)
    private val tokenBuffer = IntArray(MAX_SEQUENCE_LENGTH)

    private val selfKCache = FloatArray(SELF_CACHE_FLOATS)
    private val selfVCache = FloatArray(SELF_CACHE_FLOATS)

    private val scalarLong = LongArray(1)
    private val scalarPosLong = LongArray(1)
    private val textBuilder = StringBuilder(MAX_SEQUENCE_LENGTH * 2)

    // Reused maps to avoid per-inference/per-step allocations
    private var initSignature: String? = INIT_SIGNATURE
    private var stepSignature: String? = STEP_SIGNATURE
    private lateinit var initInputsNamed: Map<String, TensorBuffer>
    private lateinit var initOutputsNamed: Map<String, TensorBuffer>
    private lateinit var stepInputsNamed: Map<String, TensorBuffer>
    private lateinit var stepOutputsNamed: Map<String, TensorBuffer>

    private val inferenceMutex = Mutex()

    @Volatile
    private var initialized = false

    companion object {
        private const val LOG_TAG = "FastOcrEngine"
        private const val IMAGE_SIZE = 224
        private const val MAX_SEQUENCE_LENGTH = 256
        private const val MAX_DECODE_TOKENS = 96
        private const val VOCAB_SIZE = 9415
        private const val START_TOKEN_ID = 2
        private const val END_TOKEN_ID = 3
        private const val SPECIAL_TOKEN_THRESHOLD = 5
        private const val MAX_REPEAT_TOKEN_STREAK = 8

        private const val NUM_LAYERS = 4
        private const val NUM_HEADS = 4
        private const val HEAD_DIM = 64

        private const val INIT_SIGNATURE = "init"
        private const val STEP_SIGNATURE = "step"
        private val INIT_SIGNATURE_CANDIDATES = listOf("init", "serving_default", "decode_init", "init_1")
        private val STEP_SIGNATURE_CANDIDATES = listOf("step", "serving_default", "decode_step", "step_1")

        // Init signature I/O names
        private const val INIT_INPUT_ENCODER_STATES = "args_0"
        private const val INIT_INPUT_IDS = "args_1"
        private const val INIT_OUTPUT_LOGITS = "output_0"
        private const val INIT_OUTPUT_SELF_K_SLICE = "output_1"
        private const val INIT_OUTPUT_SELF_V_SLICE = "output_2"
        private const val INIT_OUTPUT_CROSS_K = "output_3"
        private const val INIT_OUTPUT_CROSS_V = "output_4"

        // Step signature I/O names
        private const val STEP_INPUT_ENCODER_STATES = "args_0"
        private const val STEP_INPUT_IDS = "args_1"
        private const val STEP_INPUT_POSITION_IDS = "args_2"
        private const val STEP_INPUT_SELF_K_CACHE = "args_3"
        private const val STEP_INPUT_SELF_V_CACHE = "args_4"
        private const val STEP_INPUT_CROSS_K = "args_5"
        private const val STEP_INPUT_CROSS_V = "args_6"
        private const val STEP_OUTPUT_LOGITS = "output_0"
        private const val STEP_OUTPUT_SELF_K_SLICE = "output_1"
        private const val STEP_OUTPUT_SELF_V_SLICE = "output_2"

        private const val SELF_CACHE_FLOATS = NUM_LAYERS * NUM_HEADS * MAX_SEQUENCE_LENGTH * HEAD_DIM

        // 0..255 -> 0.0..1.0 conversion table (precomputed to avoid per-pixel division).
        private val BYTE_TO_UNIT_FLOAT = FloatArray(256) { it * (1f / 255f) }

        @Volatile
        private var gpuDisabledInProcess = false
    }

    private data class SignatureBinding(
        val signature: String?,
        val inputBuffers: List<TensorBuffer>,
        val outputBuffers: List<TensorBuffer>,
    )

    suspend fun ensureInitialized() {
        if (initialized) return
        inferenceMutex.withLock {
            if (!initialized) {
                val ok = init()
                if (!ok) throw IllegalStateException("Failed to initialize FastOcrEngine")
            }
        }
    }

    private fun init(): Boolean {
        return try {
            // Try GPU first for speedup; if GPU compile fails once, skip GPU in this process.
            val gpuOk = if (gpuDisabledInProcess) {
                false
            } else {
                runCatching { initWithAccelerator(Accelerator.GPU, suppressInitErrorLog = true) }
                    .onFailure { error ->
                        gpuDisabledInProcess = true
                        Log.w(LOG_TAG, "GPU init failed once, disable GPU for current process", error)
                    }
                    .getOrDefault(false)
            }
            if (gpuOk) {
                Log.i(LOG_TAG, "OCR (fast) using GPU accelerator")
                return true
            }
            Log.i(
                LOG_TAG,
                if (gpuDisabledInProcess) "GPU disabled in process, using CPU"
                else "GPU not available, falling back to CPU"
            )
            initWithAccelerator(Accelerator.CPU, suppressInitErrorLog = false)
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "Failed to initialize OCR (fast) models", e)
            closeInternal()
            false
        }
    }

    private fun initWithAccelerator(
        accelerator: Accelerator,
        suppressInitErrorLog: Boolean,
    ): Boolean {
        return try {
            val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val options = CompiledModel.Options(accelerator).apply {
                if (accelerator == Accelerator.CPU) {
                    cpuOptions = CompiledModel.CpuOptions(cpuThreads, null, null)
                }
            }

            encoderModel = CompiledModel.create(
                encoderModelPath,
                options,
                environment,
            )

            decoderModel = CompiledModel.create(
                decoderModelPath,
                options,
                environment,
            )

            val encInputs = encoderModel.createInputBuffers()
            val encOutputs = encoderModel.createOutputBuffers()
            encoderImageInput = encInputs[0]
            encoderHiddenStatesOutput = encOutputs[0]

            val initBinding = resolveSignatureBinding(
                model = decoderModel,
                preferredCandidates = INIT_SIGNATURE_CANDIDATES,
                expectedInputCount = 2,
                expectedOutputCount = 5,
            )
            initSignature = initBinding.signature
            initEncoderStatesInput = initBinding.inputBuffers[0]
            initInputIdsInput = initBinding.inputBuffers[1]
            initLogitsOutput = initBinding.outputBuffers[0]
            initSelfKSliceOutput = initBinding.outputBuffers[1]
            initSelfVSliceOutput = initBinding.outputBuffers[2]
            initCrossKOutput = initBinding.outputBuffers[3]
            initCrossVOutput = initBinding.outputBuffers[4]

            val stepBinding = resolveSignatureBinding(
                model = decoderModel,
                preferredCandidates = STEP_SIGNATURE_CANDIDATES,
                expectedInputCount = 7,
                expectedOutputCount = 3,
            )
            stepSignature = stepBinding.signature
            stepEncoderStatesInput = stepBinding.inputBuffers[0]
            stepInputIdsInput = stepBinding.inputBuffers[1]
            stepPositionIdsInput = stepBinding.inputBuffers[2]
            stepSelfKCacheInput = stepBinding.inputBuffers[3]
            stepSelfVCacheInput = stepBinding.inputBuffers[4]
            stepCrossKInput = stepBinding.inputBuffers[5]
            stepCrossVInput = stepBinding.inputBuffers[6]
            stepLogitsOutput = stepBinding.outputBuffers[0]
            stepSelfKSliceOutput = stepBinding.outputBuffers[1]
            stepSelfVSliceOutput = stepBinding.outputBuffers[2]

            // Prefer deterministic named binding when decoder exposes expected names.
            // This avoids potential input-order ambiguity across model exports.
            if (initSignature != null && stepSignature != null) {
                runCatching {
                    initEncoderStatesInput = createNamedInputBuffer(decoderModel, initSignature!!, INIT_INPUT_ENCODER_STATES)
                    initInputIdsInput = createNamedInputBuffer(decoderModel, initSignature!!, INIT_INPUT_IDS)
                    initLogitsOutput = createNamedOutputBuffer(decoderModel, initSignature!!, INIT_OUTPUT_LOGITS)
                    initSelfKSliceOutput = createNamedOutputBuffer(decoderModel, initSignature!!, INIT_OUTPUT_SELF_K_SLICE)
                    initSelfVSliceOutput = createNamedOutputBuffer(decoderModel, initSignature!!, INIT_OUTPUT_SELF_V_SLICE)
                    initCrossKOutput = createNamedOutputBuffer(decoderModel, initSignature!!, INIT_OUTPUT_CROSS_K)
                    initCrossVOutput = createNamedOutputBuffer(decoderModel, initSignature!!, INIT_OUTPUT_CROSS_V)

                    stepEncoderStatesInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_ENCODER_STATES)
                    stepInputIdsInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_IDS)
                    stepPositionIdsInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_POSITION_IDS)
                    stepSelfKCacheInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_SELF_K_CACHE)
                    stepSelfVCacheInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_SELF_V_CACHE)
                    stepCrossKInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_CROSS_K)
                    stepCrossVInput = createNamedInputBuffer(decoderModel, stepSignature!!, STEP_INPUT_CROSS_V)
                    stepLogitsOutput = createNamedOutputBuffer(decoderModel, stepSignature!!, STEP_OUTPUT_LOGITS)
                    stepSelfKSliceOutput = createNamedOutputBuffer(decoderModel, stepSignature!!, STEP_OUTPUT_SELF_K_SLICE)
                    stepSelfVSliceOutput = createNamedOutputBuffer(decoderModel, stepSignature!!, STEP_OUTPUT_SELF_V_SLICE)
                }.onFailure {
                    Log.w(LOG_TAG, "Named decoder buffer binding failed, fallback to index binding", it)
                }
            }

            initInputsNamed = mapOf(
                INIT_INPUT_ENCODER_STATES to initEncoderStatesInput,
                INIT_INPUT_IDS to initInputIdsInput,
            )
            initOutputsNamed = mapOf(
                INIT_OUTPUT_LOGITS to initLogitsOutput,
                INIT_OUTPUT_SELF_K_SLICE to initSelfKSliceOutput,
                INIT_OUTPUT_SELF_V_SLICE to initSelfVSliceOutput,
                INIT_OUTPUT_CROSS_K to initCrossKOutput,
                INIT_OUTPUT_CROSS_V to initCrossVOutput,
            )
            stepInputsNamed = mapOf(
                STEP_INPUT_ENCODER_STATES to stepEncoderStatesInput,
                STEP_INPUT_IDS to stepInputIdsInput,
                STEP_INPUT_POSITION_IDS to stepPositionIdsInput,
                STEP_INPUT_SELF_K_CACHE to stepSelfKCacheInput,
                STEP_INPUT_SELF_V_CACHE to stepSelfVCacheInput,
                STEP_INPUT_CROSS_K to stepCrossKInput,
                STEP_INPUT_CROSS_V to stepCrossVInput,
            )
            stepOutputsNamed = mapOf(
                STEP_OUTPUT_LOGITS to stepLogitsOutput,
                STEP_OUTPUT_SELF_K_SLICE to stepSelfKSliceOutput,
                STEP_OUTPUT_SELF_V_SLICE to stepSelfVSliceOutput,
            )

            Log.i(
                LOG_TAG,
                "OCR (fast) decoder signatures resolved: init=${initSignature ?: "<default>"} step=${stepSignature ?: "<default>"}"
            )

            // Preprocessing scratch
            scratchBitmap = createBitmap(IMAGE_SIZE, IMAGE_SIZE)
            scratchCanvas = Canvas(scratchBitmap)
            scratchPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                isAntiAlias = true
            }

            initialized = true
            Log.i(LOG_TAG, "OCR (fast) models initialized (CPU threads=$cpuThreads)")
            true
        } catch (e: Throwable) {
            if (!suppressInitErrorLog) {
                Log.e(LOG_TAG, "Failed to initialize OCR (fast) models", e)
            }
            closeInternal()
            false
        }
    }

    private fun createNamedInputBuffer(model: CompiledModel, signature: String, name: String): TensorBuffer {
        return model.createInputBuffer(name, signature)
    }

    private fun createNamedOutputBuffer(model: CompiledModel, signature: String, name: String): TensorBuffer {
        return model.createOutputBuffer(name, signature)
    }

    private fun resolveSignatureBinding(
        model: CompiledModel,
        preferredCandidates: List<String>,
        expectedInputCount: Int,
        expectedOutputCount: Int,
    ): SignatureBinding {
        val attempts = linkedSetOf<String?>().apply {
            preferredCandidates.forEach { add(it) }
            add(null)
        }
        var lastError: Throwable? = null
        for (candidate in attempts) {
            val result = runCatching {
                val inputs = if (candidate == null) model.createInputBuffers() else model.createInputBuffers(candidate)
                val outputs = if (candidate == null) model.createOutputBuffers() else model.createOutputBuffers(candidate)
                SignatureBinding(
                    signature = candidate,
                    inputBuffers = inputs,
                    outputBuffers = outputs,
                )
            }
            if (result.isFailure) {
                lastError = result.exceptionOrNull()
                continue
            }
            val binding = result.getOrThrow()
            if (binding.inputBuffers.size == expectedInputCount && binding.outputBuffers.size == expectedOutputCount) {
                return binding
            }
        }
        throw IllegalStateException(
            "No compatible decoder signature found (expected in/out=$expectedInputCount/$expectedOutputCount)",
            lastError,
        )
    }

    private fun runDecoderModel(
        model: CompiledModel,
        inputBuffers: List<TensorBuffer>,
        outputBuffers: List<TensorBuffer>,
        signature: String?,
        inputMap: Map<String, TensorBuffer>? = null,
        outputMap: Map<String, TensorBuffer>? = null,
    ) {
        if (signature != null && inputMap != null && outputMap != null) {
            runCatching {
                model.run(inputMap, outputMap, signature)
            }.recoverCatching {
                model.run(inputBuffers, outputBuffers, signature)
            }.recoverCatching {
                model.run(inputBuffers, outputBuffers)
            }.getOrThrow()
            return
        }
        if (signature == null) model.run(inputBuffers, outputBuffers)
        else model.run(inputBuffers, outputBuffers, signature)
    }

    suspend fun recognizeText(image: Bitmap): String {
        ensureInitialized()

        val startTime = System.nanoTime()
        val rawText = inferenceMutex.withLock {
            require(!image.isRecycled) { "Input bitmap is recycled" }

            val preprocessTime = measureNanoTime {
                preprocessImage(image)
            }
            Log.i(LOG_TAG, "OCR(fast) Runtime: preprocessImage took ${preprocessTime / 1_000_000} ms")

            val encoderHiddenStates: FloatArray
            val encoderTime = measureNanoTime {
                encoderHiddenStates = runEncoder()
            }
            Log.i(LOG_TAG, "OCR(fast) Runtime: runEncoder took ${encoderTime / 1_000_000} ms")

            val tokenCount: Int
            val decoderTime = measureNanoTime {
                tokenCount = runDecoder(encoderHiddenStates)
            }
            Log.i(LOG_TAG, "OCR(fast) Runtime: runDecoder took ${decoderTime / 1_000_000} ms")

            val decodedText: String
            val decodeTokensTime = measureNanoTime {
                decodedText = decodeTokens(tokenBuffer, tokenCount)
            }
            Log.i(LOG_TAG, "OCR(fast) Runtime: decodeTokens took ${decodeTokensTime / 1_000_000} ms for ${decodedText.length + 2} tokens")

            decodedText
        }

        val postprocessedText: String
        val postprocessTime = measureNanoTime {
            postprocessedText = textPostprocessor.postprocess(rawText)
        }
        Log.i(LOG_TAG, "OCR(fast) Runtime: postprocess took ${postprocessTime / 1_000_000} ms")

        val totalTime = (System.nanoTime() - startTime) / 1_000_000
        Log.i(LOG_TAG, "OCR(fast) Runtime: recognizeText total time: $totalTime ms")

        return postprocessedText
    }

    private fun preprocessImage(bitmap: Bitmap) {
        // Draw scaled bitmap into a white 224x224 canvas (aspect-preserving + centered)
        scratchCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)

        val sw = bitmap.width.toFloat()
        val sh = bitmap.height.toFloat()
        val scale = minOf(IMAGE_SIZE / sw, IMAGE_SIZE / sh)
        val dw = (sw * scale).toInt().coerceAtLeast(1)
        val dh = (sh * scale).toInt().coerceAtLeast(1)

        val left = (IMAGE_SIZE - dw) / 2
        val top = (IMAGE_SIZE - dh) / 2
        dstRect.set(left, top, left + dw, top + dh)

        scratchCanvas.drawBitmap(bitmap, null, dstRect, scratchPaint)

        scratchBitmap.getPixels(pixelsBuffer, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // Convert to NCHW float values in [0..1]
        val lut = BYTE_TO_UNIT_FLOAT
        val hw = IMAGE_SIZE * IMAGE_SIZE
        for (i in 0 until hw) {
            val p = pixelsBuffer[i]
            nchwBuffer[i] = lut[(p shr 16) and 0xFF]
            nchwBuffer[hw + i] = lut[(p shr 8) and 0xFF]
            nchwBuffer[2 * hw + i] = lut[p and 0xFF]
        }

        encoderImageInput.writeFloat(nchwBuffer)
    }

    private fun runEncoder(): FloatArray {
        val inputBuffers = listOf(encoderImageInput)
        val outputBuffers = listOf(encoderHiddenStatesOutput)
        encoderModel.run(inputBuffers, outputBuffers)
        return encoderHiddenStatesOutput.readFloat()
    }

    private fun runDecoder(encoderHiddenStates: FloatArray): Int {
        val tokenIds = tokenBuffer
        tokenIds[0] = START_TOKEN_ID
        var tokenCount = 1
        val decodeTokenLimit = minOf(MAX_SEQUENCE_LENGTH, MAX_DECODE_TOKENS)

        selfKCache.fill(0f)
        selfVCache.fill(0f)

        // Decoder init signature (first inference)
        initEncoderStatesInput.writeFloat(encoderHiddenStates)
        scalarLong[0] = START_TOKEN_ID.toLong()
        initInputIdsInput.writeLong(scalarLong)

        runDecoderModel(
            model = decoderModel,
            inputBuffers = listOf(initEncoderStatesInput, initInputIdsInput),
            outputBuffers = listOf(
                initLogitsOutput,
                initSelfKSliceOutput,
                initSelfVSliceOutput,
                initCrossKOutput,
                initCrossVOutput,
            ),
            signature = initSignature,
            inputMap = initInputsNamed,
            outputMap = initOutputsNamed,
        )

        val logits0 = initLogitsOutput.readFloat()
        val initSelfKSlice = initSelfKSliceOutput.readFloat()
        val initSelfVSlice = initSelfVSliceOutput.readFloat()
        val crossK = initCrossKOutput.readFloat()
        val crossV = initCrossVOutput.readFloat()

        insertKvSlice(selfKCache, initSelfKSlice, seqIndex = 0)
        insertKvSlice(selfVCache, initSelfVSlice, seqIndex = 0)

        // Cross-attention tensors from init are written directly to step inputs.
        // This avoids shape mismatch when model-export dimensions differ from hardcoded constants.
        stepCrossKInput.writeFloat(crossK)
        stepCrossVInput.writeFloat(crossV)

        // Encoder hidden states are constant for all decoder steps
        stepEncoderStatesInput.writeFloat(encoderHiddenStates)

        // First generated token from init logits
        var nextToken = findMaxToken(logits0)
        if (nextToken == END_TOKEN_ID) {
            return tokenCount
        }
        tokenIds[tokenCount++] = nextToken

        var cacheLen = 1 // start token already cached at seqIndex 0
        var currentToken = nextToken
        var lastGeneratedToken = nextToken
        var repeatTokenStreak = 1

        // Decoder step signature (subsequent inferences w/ KV cache for speed)
        while (tokenCount < decodeTokenLimit && cacheLen < decodeTokenLimit) {
            scalarLong[0] = currentToken.toLong()
            stepInputIdsInput.writeLong(scalarLong)

            scalarPosLong[0] = (cacheLen + 1).toLong()
            stepPositionIdsInput.writeLong(scalarPosLong)

            stepSelfKCacheInput.writeFloat(selfKCache)
            stepSelfVCacheInput.writeFloat(selfVCache)

            runDecoderModel(
                model = decoderModel,
                inputBuffers = listOf(
                    stepEncoderStatesInput,
                    stepInputIdsInput,
                    stepPositionIdsInput,
                    stepSelfKCacheInput,
                    stepSelfVCacheInput,
                    stepCrossKInput,
                    stepCrossVInput,
                ),
                outputBuffers = listOf(
                    stepLogitsOutput,
                    stepSelfKSliceOutput,
                    stepSelfVSliceOutput,
                ),
                signature = stepSignature,
                inputMap = stepInputsNamed,
                outputMap = stepOutputsNamed,
            )

            val stepSelfKSlice = stepSelfKSliceOutput.readFloat()
            val stepSelfVSlice = stepSelfVSliceOutput.readFloat()

            insertKvSlice(selfKCache, stepSelfKSlice, seqIndex = cacheLen)
            insertKvSlice(selfVCache, stepSelfVSlice, seqIndex = cacheLen)
            cacheLen++

            val logits = stepLogitsOutput.readFloat()
            nextToken = findMaxToken(logits)
            if (nextToken == END_TOKEN_ID) {
                break
            }
            if (nextToken == lastGeneratedToken) {
                repeatTokenStreak++
                if (repeatTokenStreak >= MAX_REPEAT_TOKEN_STREAK) {
                    break
                }
            } else {
                lastGeneratedToken = nextToken
                repeatTokenStreak = 1
            }

            tokenIds[tokenCount++] = nextToken
            currentToken = nextToken
        }

        return tokenCount
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun findMaxToken(logits: FloatArray): Int {
        var maxLogit = Float.NEGATIVE_INFINITY
        var maxToken = 0

        val limit = minOf(VOCAB_SIZE, logits.size)
        for (i in 0 until limit) {
            val logit = logits[i]
            if (logit > maxLogit) {
                maxLogit = logit
                maxToken = i
            }
        }

        return maxToken
    }

    /**
     * Inserts a KV slice shaped [L,1,H,1,D] into a full cache shaped [L,1,H,S,D] at [seqIndex].
     */
    private fun insertKvSlice(fullCache: FloatArray, slice: FloatArray, seqIndex: Int) {
        if (seqIndex !in 0 until MAX_SEQUENCE_LENGTH) return

        var sliceOffset = 0
        for (layer in 0 until NUM_LAYERS) {
            for (head in 0 until NUM_HEADS) {
                val dstBase = ((layer * NUM_HEADS + head) * MAX_SEQUENCE_LENGTH + seqIndex) * HEAD_DIM
                System.arraycopy(slice, sliceOffset, fullCache, dstBase, HEAD_DIM)
                sliceOffset += HEAD_DIM
            }
        }
    }

    private fun decodeTokens(tokenIds: IntArray, tokenCount: Int): String {
        val text = textBuilder
        text.setLength(0)

        for (index in 0 until tokenCount) {
            val tokenId = tokenIds[index]
            if (tokenId < SPECIAL_TOKEN_THRESHOLD) continue
            if (tokenId in vocabFast.indices) {
                text.append(vocabFast[tokenId])
            }
        }

        return text.toString()
    }

    fun close() {
        initialized = false
        closeInternal()
    }

    private fun closeInternal() {
        try {
            if (::encoderImageInput.isInitialized) encoderImageInput.close()
            if (::encoderHiddenStatesOutput.isInitialized) encoderHiddenStatesOutput.close()

            if (::initEncoderStatesInput.isInitialized) initEncoderStatesInput.close()
            if (::initInputIdsInput.isInitialized) initInputIdsInput.close()
            if (::initLogitsOutput.isInitialized) initLogitsOutput.close()
            if (::initSelfKSliceOutput.isInitialized) initSelfKSliceOutput.close()
            if (::initSelfVSliceOutput.isInitialized) initSelfVSliceOutput.close()
            if (::initCrossKOutput.isInitialized) initCrossKOutput.close()
            if (::initCrossVOutput.isInitialized) initCrossVOutput.close()

            if (::stepEncoderStatesInput.isInitialized) stepEncoderStatesInput.close()
            if (::stepInputIdsInput.isInitialized) stepInputIdsInput.close()
            if (::stepPositionIdsInput.isInitialized) stepPositionIdsInput.close()
            if (::stepSelfKCacheInput.isInitialized) stepSelfKCacheInput.close()
            if (::stepSelfVCacheInput.isInitialized) stepSelfVCacheInput.close()
            if (::stepCrossKInput.isInitialized) stepCrossKInput.close()
            if (::stepCrossVInput.isInitialized) stepCrossVInput.close()
            if (::stepLogitsOutput.isInitialized) stepLogitsOutput.close()
            if (::stepSelfKSliceOutput.isInitialized) stepSelfKSliceOutput.close()
            if (::stepSelfVSliceOutput.isInitialized) stepSelfVSliceOutput.close()

            if (::encoderModel.isInitialized) encoderModel.close()
            if (::decoderModel.isInitialized) decoderModel.close()

            if (::scratchBitmap.isInitialized && !scratchBitmap.isRecycled) {
                scratchBitmap.recycle()
            }

            Log.i(LOG_TAG, "OCR (fast) models closed successfully")
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "Error closing OCR (fast) models", e)
        }
    }
}
