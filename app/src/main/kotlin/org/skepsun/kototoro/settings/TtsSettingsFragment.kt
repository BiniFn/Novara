package org.skepsun.kototoro.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.reader.novel.tts.LegadoTtsParser
import org.skepsun.kototoro.reader.novel.tts.engine.HttpTTSEngine
import org.skepsun.kototoro.reader.novel.tts.engine.SystemTTSEngine
import org.skepsun.kototoro.reader.novel.tts.engine.TTSEngine
import org.skepsun.kototoro.reader.novel.tts.model.Token
import org.skepsun.kototoro.reader.novel.tts.model.TokenType
import org.skepsun.kototoro.reader.novel.tts.model.TtsHttpConfig
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.compose.TtsSettingsScreen
import org.skepsun.kototoro.settings.compose.TtsSettingsUiState
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TtsSettingsFragment : Fragment() {

    @Inject
    lateinit var appSettings: AppSettings

    private var localTts: TextToSpeech? = null
    private var testMediaPlayer: MediaPlayer? = null

    private val systemVoiceOptionsFlow = MutableStateFlow<List<SettingsChoiceOption<String>>>(emptyList())
    private val systemVoiceSummaryFlow = MutableStateFlow<String?>(null)
    private val legadoVoiceOptionsFlow = MutableStateFlow<List<SettingsChoiceOption<String>>>(emptyList())
    private val legadoVoiceSummaryFlow = MutableStateFlow<String?>(null)
    private val legadoConfigCountFlow = MutableStateFlow(0)
    private val isTestRunningFlow = MutableStateFlow(false)

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_LEGADO_TTS_CONFIGS) {
            updateLegadoVoiceOptions()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appSettings.prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        updateLegadoVoiceOptions()
        initializeSystemVoices()

        (view as ComposeView).setContent {
            val enabled = appSettings.observeAsState(KEY_TTS_ENABLED) {
                prefs.getBoolean(KEY_TTS_ENABLED, true)
            }.value
            val engineType = appSettings.observeAsState(KEY_TTS_ENGINE_TYPE) {
                prefs.getString(KEY_TTS_ENGINE_TYPE, ENGINE_SYSTEM) ?: ENGINE_SYSTEM
            }.value
            val systemVoice = appSettings.observeAsState(KEY_TTS_SYSTEM_VOICE) {
                prefs.getString(KEY_TTS_SYSTEM_VOICE, DEFAULT_VOICE_VALUE) ?: DEFAULT_VOICE_VALUE
            }.value
            val legadoVoice = appSettings.observeAsState(KEY_TTS_LEGADO_VOICE) {
                prefs.getString(KEY_TTS_LEGADO_VOICE, "") ?: ""
            }.value
            val systemVoiceOptions by systemVoiceOptionsFlow.collectAsState()
            val systemVoiceSummary by systemVoiceSummaryFlow.collectAsState()
            val legadoVoiceOptions by legadoVoiceOptionsFlow.collectAsState()
            val legadoVoiceSummary by legadoVoiceSummaryFlow.collectAsState()
            val legadoConfigCount by legadoConfigCountFlow.collectAsState()
            val isTestRunning by isTestRunningFlow.collectAsState()

            KototoroTheme {
                TtsSettingsScreen(
                    state = TtsSettingsUiState(
                        enabled = enabled,
                        engineType = engineType,
                        systemVoice = systemVoice,
                        systemVoiceOptions = systemVoiceOptions,
                        systemVoiceSummary = systemVoiceSummary,
                        legadoVoice = legadoVoice,
                        legadoVoiceOptions = legadoVoiceOptions,
                        legadoVoiceSummary = legadoVoiceSummary,
                        legadoConfigCount = legadoConfigCount,
                        isTestRunning = isTestRunning,
                    ),
                    onEnabledChange = { checked ->
                        appSettings.prefs.edit { putBoolean(KEY_TTS_ENABLED, checked) }
                    },
                    onEngineTypeChange = { value ->
                        appSettings.prefs.edit { putString(KEY_TTS_ENGINE_TYPE, value) }
                    },
                    onSystemVoiceChange = { value ->
                        appSettings.prefs.edit { putString(KEY_TTS_SYSTEM_VOICE, value) }
                    },
                    onLegadoVoiceChange = { value ->
                        appSettings.prefs.edit { putString(KEY_TTS_LEGADO_VOICE, value) }
                    },
                    onTestClick = ::testTtsVoice,
                    onImportClipboardClick = ::importFromClipboard,
                    onImportUrlClick = ::importFromUrl,
                    onManageSourcesClick = ::manageLegadoSources,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.tts_settings_title))
    }

    override fun onDestroyView() {
        appSettings.prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        localTts?.shutdown()
        localTts = null
        testMediaPlayer?.release()
        testMediaPlayer = null
        super.onDestroyView()
    }

    private fun initializeSystemVoices() {
        systemVoiceOptionsFlow.value = defaultSystemVoiceOptions()
        systemVoiceSummaryFlow.value = getString(R.string.loading_)

        localTts?.shutdown()
        localTts = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.SUCCESS) {
                systemVoiceSummaryFlow.value = getString(R.string.tts_system_voice_unavailable)
                return@TextToSpeech
            }

            val voices = try {
                localTts?.voices?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            if (voices.isNotEmpty()) {
                val options = voices
                    .sortedBy { it.locale.displayName }
                    .map { voice ->
                        SettingsChoiceOption(
                            value = voice.name,
                            label = "${voice.locale.displayName} (${voice.name})",
                        )
                    }
                systemVoiceOptionsFlow.value = mergeCurrentSelection(
                    currentValue = appSettings.prefs.getString(KEY_TTS_SYSTEM_VOICE, DEFAULT_VOICE_VALUE),
                    options = options,
                    defaultOption = SettingsChoiceOption(
                        value = DEFAULT_VOICE_VALUE,
                        label = getString(R.string.tts_system_voice_default),
                    ),
                )
                systemVoiceSummaryFlow.value = null
                return@TextToSpeech
            }

            val locales = try {
                localTts?.availableLanguages?.toList().orEmpty().sortedBy { it.displayName }
            } catch (_: Exception) {
                emptyList()
            }

            if (locales.isNotEmpty()) {
                val options = locales.map { locale ->
                    SettingsChoiceOption(
                        value = locale.toLanguageTag(),
                        label = locale.displayName,
                    )
                }
                systemVoiceOptionsFlow.value = mergeCurrentSelection(
                    currentValue = appSettings.prefs.getString(KEY_TTS_SYSTEM_VOICE, DEFAULT_VOICE_VALUE),
                    options = options,
                    defaultOption = SettingsChoiceOption(
                        value = DEFAULT_VOICE_VALUE,
                        label = getString(R.string.tts_system_voice_default),
                    ),
                )
                systemVoiceSummaryFlow.value = getString(R.string.tts_system_voice_fallback)
            } else {
                systemVoiceOptionsFlow.value = defaultSystemVoiceOptions()
                systemVoiceSummaryFlow.value = getString(R.string.tts_system_voice_unavailable)
            }
        }
    }

    private fun updateLegadoVoiceOptions() {
        val configs = parseLegadoConfigs()
        legadoConfigCountFlow.value = configs.size

        if (configs.isEmpty()) {
            legadoVoiceOptionsFlow.value = emptyList()
            legadoVoiceSummaryFlow.value = getString(R.string.tts_legado_voice_unavailable)
            if (!appSettings.prefs.getString(KEY_TTS_LEGADO_VOICE, "").isNullOrEmpty()) {
                appSettings.prefs.edit { putString(KEY_TTS_LEGADO_VOICE, "") }
            }
            return
        }

        legadoVoiceOptionsFlow.value = mergeCurrentSelection(
            currentValue = appSettings.prefs.getString(KEY_TTS_LEGADO_VOICE, ""),
            options = configs.map { config ->
                SettingsChoiceOption(
                    value = config.url,
                    label = config.name.take(30) + if (config.name.length > 30) "..." else "",
                )
            },
        )
        legadoVoiceSummaryFlow.value = null
    }

    private fun testTtsVoice() {
        Toast.makeText(requireContext(), R.string.tts_test_generating, Toast.LENGTH_SHORT).show()
        isTestRunningFlow.value = true

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var engine: TTSEngine? = null
            var shouldResetState = true
            try {
                val prefs = appSettings.prefs
                val engineId = prefs.getString(KEY_TTS_ENGINE_TYPE, ENGINE_SYSTEM) ?: ENGINE_SYSTEM
                engine = if (engineId == ENGINE_LEGADO) {
                    val url = prefs.getString(KEY_TTS_LEGADO_VOICE, "") ?: ""
                    val config = parseLegadoConfigs().find { it.url == url }
                        ?: error(getString(R.string.tts_legado_voice_unavailable))
                    HttpTTSEngine(
                        client = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build(),
                        config = config,
                        context = requireContext(),
                    )
                } else {
                    SystemTTSEngine(requireContext())
                }

                val testText = getString(R.string.tts_test_phrase)
                val testToken = Token(
                    id = System.currentTimeMillis(),
                    text = testText,
                    type = TokenType.NARRATION,
                    range = testText.indices,
                )
                val result = engine.synthesize(testToken)

                withContext(Dispatchers.Main) {
                    val audioData = result.getOrNull()
                        ?: error(result.exceptionOrNull()?.message ?: getString(R.string.reader_translation_task_state_failed))
                    testMediaPlayer?.release()
                    val player = MediaPlayer.create(requireContext(), audioData.uri)
                        ?: error(getString(R.string.reader_translation_task_state_failed))
                    testMediaPlayer = player
                    player.setOnCompletionListener {
                        it.release()
                        testMediaPlayer = null
                        isTestRunningFlow.value = false
                    }
                    player.setOnErrorListener { mediaPlayer, _, _ ->
                        mediaPlayer.release()
                        testMediaPlayer = null
                        isTestRunningFlow.value = false
                        true
                    }
                    player.start()
                    shouldResetState = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tts_test_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                engine?.release()
                if (shouldResetState) {
                    withContext(Dispatchers.Main) {
                        isTestRunningFlow.value = false
                    }
                }
            }
        }
    }

    private fun saveLegadoConfigs(newConfigs: List<TtsHttpConfig>) {
        val existingConfigs = parseLegadoConfigs().toMutableList()
        val urls = existingConfigs.map { it.url }.toSet()
        existingConfigs += newConfigs.filter { it.url !in urls }
        appSettings.prefs.edit {
            putString(KEY_LEGADO_TTS_CONFIGS, Gson().toJson(existingConfigs))
        }
    }

    private fun manageLegadoSources() {
        val configs = parseLegadoConfigs().toMutableList()
        if (configs.isEmpty()) {
            Toast.makeText(requireContext(), R.string.tts_legado_sources_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val names = configs.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(configs.size)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tts_legado_manage_delete_title)
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.tts_legado_manage_delete_action) { _, _ ->
                val remaining = configs.filterIndexed { index, _ -> !checkedItems[index] }
                if (remaining.size == configs.size) return@setPositiveButton

                appSettings.prefs.edit {
                    putString(KEY_LEGADO_TTS_CONFIGS, Gson().toJson(remaining))
                    val currentVoice = appSettings.prefs.getString(KEY_TTS_LEGADO_VOICE, "")
                    if (currentVoice != null && remaining.none { it.url == currentVoice }) {
                        putString(KEY_TTS_LEGADO_VOICE, "")
                    }
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.tts_legado_sources_deleted, configs.size - remaining.size),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importFromUrl() {
        val context = requireActivity()
        val input = EditText(context).apply {
            hint = URL_HINT
            isSingleLine = true
        }

        val container = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        val margin = (24 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, 0, margin, 0)
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tts_legado_import_dialog_title)
            .setMessage(R.string.tts_legado_import_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.tts_legado_import_url) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    downloadAndImportUrl(url)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndImportUrl(url: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    check(body.isNotBlank()) { "Empty response body" }

                    val configs = LegadoTtsParser.parseList(body)
                    withContext(Dispatchers.Main) {
                        if (configs.isNotEmpty()) {
                            saveLegadoConfigs(configs)
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.tts_legado_import_success, configs.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(requireContext(), R.string.tts_legado_import_empty, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tts_legado_import_download_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.tts_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val configs = LegadoTtsParser.parseList(text)
                withContext(Dispatchers.Main) {
                    if (configs.isNotEmpty()) {
                        saveLegadoConfigs(configs)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.tts_legado_import_success, configs.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), R.string.tts_legado_import_parse_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.tts_legado_import_parse_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseLegadoConfigs(): List<TtsHttpConfig> {
        val currentJson = appSettings.prefs.getString(KEY_LEGADO_TTS_CONFIGS, "[]") ?: "[]"
        val type = object : TypeToken<List<TtsHttpConfig>>() {}.type
        return try {
            Gson().fromJson<List<TtsHttpConfig>>(currentJson, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun defaultSystemVoiceOptions(): List<SettingsChoiceOption<String>> {
        return listOf(
            SettingsChoiceOption(
                value = DEFAULT_VOICE_VALUE,
                label = getString(R.string.tts_system_voice_default),
            ),
        )
    }

    private fun mergeCurrentSelection(
        currentValue: String?,
        options: List<SettingsChoiceOption<String>>,
        defaultOption: SettingsChoiceOption<String>? = null,
    ): List<SettingsChoiceOption<String>> {
        return buildList {
            defaultOption?.let(::add)
            if (!currentValue.isNullOrBlank() && options.none { it.value == currentValue } && currentValue != defaultOption?.value) {
                add(SettingsChoiceOption(currentValue, currentValue))
            }
            addAll(options)
        }.distinctBy { it.value }
    }

    private companion object {
        const val KEY_TTS_ENABLED = "tts_enabled"
        const val KEY_TTS_ENGINE_TYPE = "tts_engine_type"
        const val KEY_TTS_SYSTEM_VOICE = "tts_system_voice"
        const val KEY_TTS_LEGADO_VOICE = "tts_legado_voice"
        const val KEY_LEGADO_TTS_CONFIGS = "legado_tts_configs"
        const val ENGINE_SYSTEM = "SYSTEM"
        const val ENGINE_LEGADO = "LEGADO"
        const val DEFAULT_VOICE_VALUE = "default"
        const val URL_HINT = "https://..."
    }
}
