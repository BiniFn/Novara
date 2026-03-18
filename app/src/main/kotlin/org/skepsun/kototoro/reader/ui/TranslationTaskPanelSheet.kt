package org.skepsun.kototoro.reader.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.copyToClipboard
import org.skepsun.kototoro.databinding.ItemTranslationTaskPanelBinding
import org.skepsun.kototoro.databinding.SheetTranslationTaskPanelBinding
import org.skepsun.kototoro.reader.domain.TranslationLayerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TranslationTaskPanelSheet : BaseAdaptiveSheet<SheetTranslationTaskPanelBinding>(), View.OnClickListener {

    private val viewModel by activityViewModels<ReaderViewModel>()
    private val adapter = TranslationTaskPanelAdapter(::onItemClick)
    private var taskFilter: TranslationTaskFilter = TranslationTaskFilter.ALL

    @Inject
    lateinit var settings: AppSettings

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): SheetTranslationTaskPanelBinding {
        return SheetTranslationTaskPanelBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: SheetTranslationTaskPanelBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(binding.root.context)
        binding.recyclerView.adapter = adapter
        binding.buttonFilter.setOnClickListener(this)
        binding.buttonRetryFailed.setOnClickListener(this)
        observePanelUpdates()
        render()
    }

    private fun observePanelUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.translationTaskPanelVersion.collect {
                    render()
                }
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.recyclerView?.updatePadding(
            bottom = insets.getInsets(typeMask).bottom + resources.getDimensionPixelSize(R.dimen.screen_padding),
        )
        return insets.consume(v, typeMask, bottom = true)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_filter -> showTaskFilterDialog()
            R.id.button_retry_failed -> {
                viewModel.retranslateFailedInCurrentChapter()
                render()
            }
        }
    }

    private fun render() {
        val binding = viewBinding ?: return
        val snapshots = viewModel.getCurrentChapterTranslationTaskSnapshots()
        if (snapshots.isEmpty()) {
            binding.textSummary.text = getString(R.string.reader_translation_task_panel_empty)
            binding.textEmpty.isVisible = true
            binding.textEmpty.text = getString(R.string.reader_translation_task_panel_empty)
            binding.recyclerView.isVisible = false
            adapter.submit(emptyList())
            return
        }

        val filtered = snapshots.filter { taskFilter.matches(it) }
        val failed = filtered.count { it.state == TranslationLayerState.FAILED }
        val generating = filtered.count { it.state == TranslationLayerState.GENERATING }
        val ready = filtered.count { it.state == TranslationLayerState.READY }
        val summary = getString(
            R.string.reader_translation_task_panel_summary,
            filtered.size,
            ready,
            generating,
            failed,
        )
        val benchmarkSummary = buildTranslationBenchmarkSummary(snapshots)
        binding.textSummary.text = buildString {
            appendLine(summary)
            append(getString(R.string.reader_translation_task_filter_current, taskFilter.label(requireContext())))
            if (benchmarkSummary.isNotBlank()) {
                appendLine()
                appendLine()
                append(benchmarkSummary)
            }
        }

        if (filtered.isEmpty()) {
            binding.textEmpty.isVisible = true
            binding.textEmpty.text = getString(
                R.string.reader_translation_task_panel_empty_for_filter,
                taskFilter.label(requireContext()),
            )
            binding.recyclerView.isVisible = false
            adapter.submit(emptyList())
            return
        }

        binding.textEmpty.isVisible = false
        binding.recyclerView.isVisible = true
        adapter.submit(buildPanelItems(filtered, benchmarkSummary))
    }

    private fun buildPanelItems(
        filtered: List<ReaderViewModel.TranslationPageTaskSnapshot>,
        benchmarkSummary: String,
    ): List<PanelItem> {
        val items = ArrayList<PanelItem>(filtered.size + 1)
        items += PanelItem.Benchmark(
            title = getString(R.string.reader_translation_task_benchmark_title),
            subtitle = benchmarkSummary.lineSequence().drop(1).firstOrNull().orEmpty()
                .ifBlank { getString(R.string.reader_translation_task_benchmark_empty) },
            detail = benchmarkSummary,
        )
        filtered.forEach { item ->
            val timeText = item.updatedAtMs?.let { updated ->
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    updated,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
            } ?: getString(R.string.reader_translation_task_time_unknown)
            val preview = item.log.lineSequence().lastOrNull().orEmpty().ifBlank {
                getString(R.string.reader_translation_page_log_empty)
            }
            items += PanelItem.Page(
                snapshot = item,
                title = getString(
                    R.string.reader_translation_task_item,
                    item.pageIndex + 1,
                    translationStateLabel(item.state),
                    timeText,
                    preview,
                ).lineSequence().firstOrNull().orEmpty(),
                subtitle = preview,
            )
        }
        return items
    }

    private fun onItemClick(item: PanelItem) {
        when (item) {
            is PanelItem.Benchmark -> showTranslationBenchmarkDetail(item.detail)
            is PanelItem.Page -> showTranslationPageDetail(item.snapshot)
        }
    }

    private fun showTaskFilterDialog() {
        val filters = TranslationTaskFilter.entries
        val labels = filters.map { it.label(requireContext()) }.toTypedArray()
        val selected = filters.indexOf(taskFilter).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_translation_task_filter_title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                taskFilter = filters[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showTranslationBenchmarkDetail(benchmarkSummary: String) {
        val snapshots = viewModel.getCurrentChapterTranslationTaskSnapshots()
        val message = buildBenchmarkExportReport(snapshots, benchmarkSummary)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reader_translation_task_benchmark_title)
            .setMessage(message)
            .setPositiveButton(androidx.preference.R.string.copy) { _, _ ->
                requireContext().copyToClipboard(getString(R.string.reader_translation_task_benchmark_title), message)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun buildBenchmarkExportReport(
        snapshots: List<ReaderViewModel.TranslationPageTaskSnapshot>,
        benchmarkSummary: String,
    ): String {
        val ready = snapshots.count { it.state == TranslationLayerState.READY }
        val generating = snapshots.count { it.state == TranslationLayerState.GENERATING }
        val failed = snapshots.count { it.state == TranslationLayerState.FAILED }
        val sampled = benchmarkSummary.lineSequence().firstOrNull { it.startsWith("已采样:") }.orEmpty()
        val mangaTitle = viewModel.getContentOrNull()?.title.orEmpty().ifBlank { "?" }
        val chapterTitle = viewModel.uiState.value?.chapter?.title.orEmpty().ifBlank { "?" }
        val onnxModel = settings.readerTranslationOnnxModelId.ifBlank { "MLKIT" }
        val detModel = settings.readerTranslationDetModelId.ifBlank { "AUTO" }
        val recModel = settings.readerTranslationRecModelId.ifBlank { "AUTO" }
        val threshold = (settings.readerTranslationHybridFallbackThreshold * 100).toInt()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("【翻译章节基线导出】")
            appendLine("时间: $generatedAt")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("漫画: $mangaTitle")
            appendLine("章节: $chapterTitle")
            appendLine(
                "配置: " +
                    "lang=${settings.readerTranslationSourceLanguage}->${settings.readerTranslationTargetLanguage}, " +
                    "mode=${settings.readerTranslationMode}, " +
                    "ocr=${settings.readerTranslationOcrEngine}, " +
                    "det=$detModel, rec=$recModel, onnx=$onnxModel, grouping=${settings.isReaderTranslationBubbleGroupingEnabled}, threshold=${threshold}%",
            )
            appendLine(
                getString(
                    R.string.reader_translation_task_panel_summary,
                    snapshots.size,
                    ready,
                    generating,
                    failed,
                ),
            )
            if (sampled.isNotBlank()) {
                appendLine(sampled)
            }
            appendLine()
            append(
                benchmarkSummary.ifBlank {
                    getString(R.string.reader_translation_task_benchmark_empty)
                },
            )
        }.trim()
    }

    private fun showTranslationPageDetail(item: ReaderViewModel.TranslationPageTaskSnapshot) {
        val title = getString(
            R.string.reader_translation_task_detail_title,
            item.pageIndex + 1,
            translationStateLabel(item.state),
        )
        val rawLog = item.log.ifBlank {
            getString(R.string.reader_translation_page_log_empty)
        }
        val report = buildTranslationDetailReport(item.log)
        val message = if (report.isBlank()) rawLog else "$report\n\n----------------\n$rawLog"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.reader_translation_retry_this_page) { _, _ ->
                viewModel.retryTranslationForPage(item.pageId)
                render()
            }
            .setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
                requireContext().copyToClipboard(title, message)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun buildTranslationDetailReport(log: String): String {
        if (log.isBlank()) return ""
        var sourceLang = "?"
        var targetLang = "?"
        var configuredOcr = "?"
        val metrics = linkedMapOf<String, String>()
        val ocrAttempts = linkedMapOf<String, Int>()
        var localRequested = -1
        var localDoneTranslated = -1
        var localDoneTotal = -1
        var renderedBubbles = -1
        var failedReason: String? = null
        var failCode: String? = null
        val timeline = ArrayList<String>(8)
        val pairs = ArrayList<Pair<String, String>>(10)

        log.lineSequence().forEach { line ->
            if (line.startsWith("metric.")) {
                val idx = line.indexOf('=')
                if (idx > 7 && idx < line.length - 1) {
                    metrics[line.substring(7, idx)] = line.substring(idx + 1).trim()
                }
            }
            if (line.contains("process start ")) {
                Regex("""sourceLang=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { sourceLang = it }
                Regex("""targetLang=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { targetLang = it }
                Regex("""ocr=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)?.let { configuredOcr = it }
                timeline.add("开始处理")
            }
            if (line.contains("process failed:")) {
                failedReason = line.substringAfter("process failed:", "").trim()
                timeline.add("处理失败")
            }
            Regex("""fail_code=([A-Z_]+)""").find(line)?.groupValues?.getOrNull(1)?.let { failCode = it }
            Regex("""ocr engine=([A-Z_]+) blocks=(\d+)""").find(line)?.let { m ->
                ocrAttempts[m.groupValues[1]] = m.groupValues[2].toIntOrNull() ?: 0
                timeline.add("OCR[${m.groupValues[1]}]=${m.groupValues[2]}")
            }
            Regex("""translate local requested size=(\d+)""").find(line)?.let { m ->
                localRequested = m.groupValues[1].toIntOrNull() ?: -1
                timeline.add("本地翻译请求=$localRequested")
            }
            Regex("""translate local batch done translated=(\d+)/(\d+)""").find(line)?.let { m ->
                localDoneTranslated = m.groupValues[1].toIntOrNull() ?: -1
                localDoneTotal = m.groupValues[2].toIntOrNull() ?: -1
                timeline.add("本地翻译完成=$localDoneTranslated/$localDoneTotal")
            }
            Regex("""render done translatedBubbles=(\d+)""").find(line)?.let { m ->
                renderedBubbles = m.groupValues[1].toIntOrNull() ?: -1
                timeline.add("渲染完成=$renderedBubbles")
            }
            Regex("""bubble translate src=(.*?) out=(.*?) box=""").find(line)?.let { m ->
                if (pairs.size < 8) {
                    pairs.add(m.groupValues[1].trim() to m.groupValues[2].trim())
                }
            }
        }

        return buildString {
            appendLine("【翻译诊断】")
            appendLine("语言: $sourceLang -> $targetLang")
            appendLine("配置 OCR: $configuredOcr")
            if (ocrAttempts.isNotEmpty()) appendLine("OCR 尝试: ${ocrAttempts.entries.joinToString { "${it.key}:${it.value}" }}")
            if (localRequested >= 0) appendLine("本地翻译: 请求 $localRequested, 完成 $localDoneTranslated/$localDoneTotal")
            if (renderedBubbles >= 0) appendLine("渲染气泡: $renderedBubbles")
            buildMetricSummary(metrics).takeIf { it.isNotEmpty() }?.let { metricLines ->
                appendLine("性能指标:")
                metricLines.forEach { appendLine(it) }
            }
            failCode?.let { appendLine("失败代码: $it") }
            failedReason?.let { appendLine("失败原因: $it") }
            if (timeline.isNotEmpty()) appendLine("阶段时间线: ${timeline.joinToString(" -> ")}")
            if (pairs.isNotEmpty()) {
                appendLine()
                appendLine("示例识别/翻译:")
                pairs.forEachIndexed { idx, (src, out) ->
                    appendLine("${idx + 1}. 原: ${src.ifBlank { "<空>" }}")
                    appendLine("   译: ${out.ifBlank { "<空>" }}")
                }
            }
        }.trim()
    }

    private fun buildMetricSummary(metrics: Map<String, String>): List<String> {
        if (metrics.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        metrics["process.total_ms"]?.let { lines += "总耗时: ${it}ms" }
        metrics["ocr.total_ms"]?.let { lines += "OCR: ${it}ms" }
        metrics["translation.total_ms"]?.let { lines += "翻译: ${it}ms" }
        metrics["render.total_ms"]?.let { lines += "渲染: ${it}ms" }
        metrics["ocr.selected_engine"]?.let { lines += "选中 OCR 引擎: $it" }
        metrics["ocr.blocks"]?.let { lines += "OCR 文本块: $it" }
        metrics["translation.bubbles"]?.let { lines += "气泡数: $it" }
        metrics["render.translated_bubbles"]?.let { lines += "已渲染气泡: $it" }
        metrics["ocr.cache_hit"]?.let { lines += "OCR 缓存: ${if (it == "1") "命中" else "未命中"}" }
        metrics["render_cache.hit"]?.let { lines += "渲染缓存: ${if (it == "1") "命中" else "未命中"}" }
        val parts = listOfNotNull(
            metrics["hybrid.ncnn_blocks"]?.let { "NCNN块=$it" },
            metrics["hybrid.fallback_candidates"]?.let { "候选=$it" },
            metrics["hybrid.feature_cache_hits"]?.let { "特征缓存命中=$it" },
            metrics["hybrid.tflite_fallbacks"]?.let { "TFLite回退=$it" },
            metrics["hybrid.fallback_rate"]?.let { "回退率=$it" },
        )
        if (parts.isNotEmpty()) lines += "Hybrid: ${parts.joinToString(" / ")}"
        val hybridTiming = listOfNotNull(
            metrics["hybrid.total_ms"]?.let { "总=$it ms" },
            metrics["hybrid.ncnn_ms"]?.let { "NCNN=$it ms" },
            metrics["hybrid.tflite_ms"]?.let { "TFLite=$it ms" },
        )
        if (hybridTiming.isNotEmpty()) lines += "Hybrid耗时: ${hybridTiming.joinToString(" / ")}"
        return lines
    }

    private fun buildTranslationBenchmarkSummary(
        snapshots: List<ReaderViewModel.TranslationPageTaskSnapshot>,
    ): String {
        val samples = snapshots.mapNotNull(::parseTranslationBenchmarkSample)
        if (samples.isEmpty()) return ""
        val lines = mutableListOf<String>()
        lines += "【章节基线】"
        lines += "已采样: ${samples.size}/${snapshots.size} 页"
        formatPercentileLine("总耗时", samples.mapNotNull { it.processTotalMs })?.let { lines += it }
        formatPercentileLine("OCR", samples.mapNotNull { it.ocrTotalMs })?.let { lines += it }
        formatPercentileLine("翻译", samples.mapNotNull { it.translationTotalMs })?.let { lines += it }
        formatPercentileLine("渲染", samples.mapNotNull { it.renderTotalMs })?.let { lines += it }
        formatBooleanRateLine("OCR 缓存命中", samples.mapNotNull { it.ocrCacheHit })?.let { lines += it }
        formatBooleanRateLine("渲染缓存命中", samples.mapNotNull { it.renderCacheHit })?.let { lines += it }
        formatDistributionLine("选中 OCR", samples.mapNotNull { it.selectedEngine })?.let { lines += it }
        formatHybridSummaryLine(samples)?.let { lines += it }
        formatDistributionLine("失败代码", samples.mapNotNull { it.failCode })?.let { lines += it }
        return lines.joinToString("\n")
    }

    private fun parseTranslationBenchmarkSample(
        snapshot: ReaderViewModel.TranslationPageTaskSnapshot,
    ): TranslationBenchmarkSample? {
        if (snapshot.log.isBlank()) return null
        val metrics = linkedMapOf<String, String>()
        snapshot.log.lineSequence().forEach { line ->
            if (!line.startsWith("metric.")) return@forEach
            val idx = line.indexOf('=')
            if (idx > 7 && idx < line.length - 1) {
                metrics[line.substring(7, idx)] = line.substring(idx + 1).trim()
            }
        }
        if (metrics.isEmpty() && snapshot.failCode == null) return null
        return TranslationBenchmarkSample(
            processTotalMs = metrics["process.total_ms"]?.toLongOrNull(),
            ocrTotalMs = metrics["ocr.total_ms"]?.toLongOrNull(),
            translationTotalMs = metrics["translation.total_ms"]?.toLongOrNull(),
            renderTotalMs = metrics["render.total_ms"]?.toLongOrNull(),
            ocrCacheHit = metrics["ocr.cache_hit"]?.toIntOrNull()?.let { it == 1 },
            renderCacheHit = metrics["render_cache.hit"]?.toIntOrNull()?.let { it == 1 },
            hybridFallbackRate = metrics["hybrid.fallback_rate"]?.toDoubleOrNull(),
            hybridTfliteFallbacks = metrics["hybrid.tflite_fallbacks"]?.toIntOrNull(),
            selectedEngine = metrics["ocr.selected_engine"]?.takeIf { it.isNotBlank() },
            failCode = snapshot.failCode,
        )
    }

    private fun formatPercentileLine(label: String, values: List<Long>): String? {
        if (values.isEmpty()) return null
        return "$label p50/p95: ${percentile(values, 0.5)}ms / ${percentile(values, 0.95)}ms"
    }

    private fun formatBooleanRateLine(label: String, values: List<Boolean>): String? {
        if (values.isEmpty()) return null
        val hitCount = values.count { it }
        return "$label: $hitCount/${values.size} (${hitCount * 100 / values.size}%)"
    }

    private fun formatDistributionLine(label: String, values: List<String>): String? {
        if (values.isEmpty()) return null
        val summary = values.groupingBy { it }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .joinToString(" / ") { "${it.key}:${it.value}" }
        return "$label: $summary"
    }

    private fun formatHybridSummaryLine(samples: List<TranslationBenchmarkSample>): String? {
        val fallbackRates = samples.mapNotNull { it.hybridFallbackRate }
        val fallbackBlocks = samples.mapNotNull { it.hybridTfliteFallbacks }
        if (fallbackRates.isEmpty() && fallbackBlocks.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (fallbackRates.isNotEmpty()) {
            parts += "回退页=${fallbackRates.count { it > 0.0 }}/${fallbackRates.size}"
            parts += "回退率 avg/p95=${formatPercent(fallbackRates.average() * 100)} / ${formatPercent((percentile(fallbackRates, 0.95) ?: 0.0) * 100)}"
        }
        if (fallbackBlocks.isNotEmpty()) {
            parts += "TFLite回退块 avg/p95=${formatDecimal(fallbackBlocks.average())} / ${percentile(fallbackBlocks, 0.95) ?: 0}"
        }
        return "Hybrid: ${parts.joinToString(" | ")}"
    }

    private fun percentile(values: List<Long>, percentile: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((ceil(percentile.coerceIn(0.0, 1.0) * sorted.size) - 1).toInt()).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun percentile(values: List<Int>, percentile: Double): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((ceil(percentile.coerceIn(0.0, 1.0) * sorted.size) - 1).toInt()).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun percentile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((ceil(percentile.coerceIn(0.0, 1.0) * sorted.size) - 1).toInt()).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun formatPercent(value: Double): String {
        return String.format(java.util.Locale.US, "%.1f%%", value)
    }

    private fun formatDecimal(value: Double): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }

    private fun translationStateLabel(state: TranslationLayerState): String {
        return when (state) {
            TranslationLayerState.IDLE -> getString(R.string.reader_translation_task_state_idle)
            TranslationLayerState.GENERATING -> getString(R.string.reader_translation_task_state_generating)
            TranslationLayerState.READY -> getString(R.string.reader_translation_task_state_ready)
            TranslationLayerState.FAILED -> getString(R.string.reader_translation_task_state_failed)
        }
    }

    sealed interface PanelItem {
        data class Benchmark(
            val title: String,
            val subtitle: String,
            val detail: String,
        ) : PanelItem

        data class Page(
            val snapshot: ReaderViewModel.TranslationPageTaskSnapshot,
            val title: String,
            val subtitle: String,
        ) : PanelItem
    }

    private data class TranslationBenchmarkSample(
        val processTotalMs: Long?,
        val ocrTotalMs: Long?,
        val translationTotalMs: Long?,
        val renderTotalMs: Long?,
        val ocrCacheHit: Boolean?,
        val renderCacheHit: Boolean?,
        val hybridFallbackRate: Double?,
        val hybridTfliteFallbacks: Int?,
        val selectedEngine: String?,
        val failCode: String?,
    )

    private enum class TranslationTaskFilter {
        ALL,
        FAILED,
        OCR_EMPTY,
        TRANSLATE_EMPTY,
        RENDER_FILTERED,
        PROCESS_EXCEPTION;

        fun matches(item: ReaderViewModel.TranslationPageTaskSnapshot): Boolean {
            return when (this) {
                ALL -> true
                FAILED -> item.state == TranslationLayerState.FAILED
                OCR_EMPTY -> item.failCode == "OCR_EMPTY"
                TRANSLATE_EMPTY -> item.failCode == "TRANSLATE_EMPTY"
                RENDER_FILTERED -> item.failCode == "RENDER_FILTERED"
                PROCESS_EXCEPTION -> item.failCode == "PROCESS_EXCEPTION"
            }
        }

        fun label(context: android.content.Context): String {
            return when (this) {
                ALL -> context.getString(R.string.reader_translation_task_filter_all)
                FAILED -> context.getString(R.string.reader_translation_task_filter_failed)
                OCR_EMPTY -> context.getString(R.string.reader_translation_task_filter_ocr_empty)
                TRANSLATE_EMPTY -> context.getString(R.string.reader_translation_task_filter_translate_empty)
                RENDER_FILTERED -> context.getString(R.string.reader_translation_task_filter_render_filtered)
                PROCESS_EXCEPTION -> context.getString(R.string.reader_translation_task_filter_exception)
            }
        }
    }

    private class TranslationTaskPanelAdapter(
        private val onItemClick: (PanelItem) -> Unit,
    ) : RecyclerView.Adapter<TranslationTaskPanelAdapter.ViewHolder>() {

        private val items = ArrayList<PanelItem>()

        fun submit(value: List<PanelItem>) {
            items.clear()
            items.addAll(value)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTranslationTaskPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onItemClick)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        class ViewHolder(
            private val binding: ItemTranslationTaskPanelBinding,
            private val onItemClick: (PanelItem) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: PanelItem) {
                val title = when (item) {
                    is PanelItem.Benchmark -> item.title
                    is PanelItem.Page -> item.title
                }
                val subtitle = when (item) {
                    is PanelItem.Benchmark -> item.subtitle
                    is PanelItem.Page -> item.subtitle
                }
                binding.textTitle.text = title
                binding.textSubtitle.text = subtitle
                binding.root.setOnClickListener { onItemClick(item) }
            }
        }
    }

    companion object {
        private const val TAG = "TranslationTaskPanelSheet"

        fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag(TAG) != null) return
            TranslationTaskPanelSheet().show(manager, TAG)
        }
    }
}
