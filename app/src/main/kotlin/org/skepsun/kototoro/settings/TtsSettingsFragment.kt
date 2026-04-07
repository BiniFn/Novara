package org.skepsun.kototoro.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import android.widget.FrameLayout
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.reader.novel.tts.LegadoTtsParser
import org.skepsun.kototoro.reader.novel.tts.model.TtsHttpConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request

@AndroidEntryPoint
class TtsSettingsFragment : 
    BasePreferenceFragment(R.string.ai_settings), 
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var localTts: TextToSpeech? = null
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_tts_settings)
        
        // Setup engine types dynamically
        findPreference<ListPreference>("tts_engine_type")?.run {
            entries = arrayOf("System TTS (Native)", "Legado Network TTS")
            entryValues = arrayOf("SYSTEM", "LEGADO")
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        
        // Prevent crash before async TTS initialization completes
        findPreference<ListPreference>("tts_system_voice")?.run {
            entries = arrayOf("Default")
            entryValues = arrayOf("default")
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        
        // Init TTS to fetch voices
        localTts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Get all available voices (ignoring language filter for now to ensure we get something)
                val voices = try { localTts?.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
                val voiceListPref = findPreference<ListPreference>("tts_system_voice")
                
                if (voices.isNotEmpty()) {
                    // Sort by locale
                    val sortedVoices = voices.sortedBy { it.locale.displayName }
                    val entries = sortedVoices.map { "${it.locale.displayName} (${it.name})" }.toTypedArray()
                    val values = sortedVoices.map { it.name }.toTypedArray()
                    
                    requireActivity().runOnUiThread {
                        voiceListPref?.entries = entries
                        voiceListPref?.entryValues = values
                        voiceListPref?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    }
                } else {
                    // Fallback to locales if getVoices() is broken (common on some OEM devices like OnePlus)
                    val locales = localTts?.availableLanguages?.toList()?.sortedBy { it.displayName } ?: emptyList()
                    if (locales.isNotEmpty()) {
                        val entries = locales.map { it.displayName }.toTypedArray()
                        val values = locales.map { it.toLanguageTag() }.toTypedArray()
                        requireActivity().runOnUiThread {
                            voiceListPref?.entries = entries
                            voiceListPref?.entryValues = values
                            voiceListPref?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                            voiceListPref?.summary = "Using language fallback (OEM TTS)"
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            voiceListPref?.summaryProvider = null
                            voiceListPref?.summary = "No compatible voices found"
                        }
                    }
                }
            }
        }

        // Apply visibility
        val isSystem = preferenceManager.sharedPreferences?.getString("tts_engine_type", "SYSTEM") == "SYSTEM"
        findPreference<PreferenceCategory>("tts_system_category")?.isVisible = isSystem
        findPreference<PreferenceCategory>("tts_legado_category")?.isVisible = !isSystem

        updateLegadoVoiceList()
        
        // Setup click listeners
        findPreference<Preference>("tts_import_legado_clipboard")?.setOnPreferenceClickListener {
            importFromClipboard()
            true
        }
        findPreference<Preference>("tts_import_legado_url")?.setOnPreferenceClickListener {
            importFromUrl()
            true
        }
        findPreference<Preference>("tts_manage_legado")?.setOnPreferenceClickListener {
            manageLegadoSources()
            true
        }
    }

    private fun updateLegadoVoiceList() {
        val prefs = preferenceManager.sharedPreferences ?: return
        val currentJson = prefs.getString("legado_tts_configs", "[]") ?: "[]"
        val type = object : TypeToken<List<TtsHttpConfig>>() {}.type
        val configs: List<TtsHttpConfig> = try {
            Gson().fromJson(currentJson, type)
        } catch (e: Exception) {
            emptyList()
        }

        findPreference<ListPreference>("tts_legado_voice")?.apply {
            if (configs.isNotEmpty()) {
                val names = configs.map { it.name.take(30) + (if (it.name.length > 30) "..." else "") }
                entries = names.toTypedArray()
                entryValues = configs.map { it.url }.toTypedArray()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isEnabled = true
            } else {
                entries = arrayOf("None")
                entryValues = arrayOf("")
                summaryProvider = null
                summary = "Please import a configuration first"
                isEnabled = false
            }
        }
    }

    private fun saveLegadoConfigs(newConfigs: List<TtsHttpConfig>) {
        val prefs = preferenceManager.sharedPreferences ?: return
        val currentJson = prefs.getString("legado_tts_configs", "[]") ?: "[]"
        val type = object : TypeToken<List<TtsHttpConfig>>() {}.type
        val existingConfigs: MutableList<TtsHttpConfig> = try {
            Gson().fromJson(currentJson, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
        
        val urls = existingConfigs.map { it.url }.toSet()
        val toAdd = newConfigs.filter { it.url !in urls }
        existingConfigs.addAll(toAdd)
        
        prefs.edit().putString("legado_tts_configs", Gson().toJson(existingConfigs)).apply()
        updateLegadoVoiceList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        localTts?.shutdown()
        super.onDestroyView()
    }

    private fun manageLegadoSources() {
        val prefs = preferenceManager.sharedPreferences ?: return
        val currentJson = prefs.getString("legado_tts_configs", "[]") ?: "[]"
        val type = object : TypeToken<List<TtsHttpConfig>>() {}.type
        val configs: MutableList<TtsHttpConfig> = try {
            Gson().fromJson(currentJson, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

        if (configs.isEmpty()) {
            Toast.makeText(requireContext(), "No imported sources to manage", Toast.LENGTH_SHORT).show()
            return
        }

        val names = configs.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(configs.size) { false }

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Manage Sources (Select to Delete)")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Delete Selected") { _, _ ->
                val remaining = configs.filterIndexed { index, _ -> !checkedItems[index] }
                if (remaining.size < configs.size) {
                    prefs.edit().putString("legado_tts_configs", Gson().toJson(remaining)).apply()
                    updateLegadoVoiceList()
                    
                    // If the active voice was deleted, reset it
                    val activeVoicePref = findPreference<ListPreference>("tts_legado_voice")
                    val activeVoiceVal = activeVoicePref?.value
                    if (activeVoiceVal != null && remaining.none { it.url == activeVoiceVal }) {
                        activeVoicePref.value = ""
                    }
                    
                    Toast.makeText(requireContext(), "Deleted ${configs.size - remaining.size} sources", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFromUrl() {
        val context = requireActivity()
        val input = EditText(context).apply {
            hint = "https://..."
            isSingleLine = true
        }
        
        val container = FrameLayout(context)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, 0, margin, 0)
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle("Import Legado JSON")
            .setMessage("Enter the URL pointing to a Legado TTS JSON configuration.")
            .setView(container)
            .setPositiveButton("Import") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    downloadAndImportUrl(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAndImportUrl(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    
                    val configs = LegadoTtsParser.parseList(body)
                    withContext(Dispatchers.Main) {
                        if (configs.isNotEmpty()) {
                            saveLegadoConfigs(configs)
                            Toast.makeText(requireContext(), "Imported ${configs.size} Legado configs!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "No readable Legado configuration found in JSON", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val configs = LegadoTtsParser.parseList(text)
                withContext(Dispatchers.Main) {
                    if (configs.isNotEmpty()) {
                        saveLegadoConfigs(configs)
                        Toast.makeText(requireContext(), "Imported ${configs.size} Legado configs!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to parse Legado JSON", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "tts_engine_type" -> {
                val isSystem = sharedPreferences?.getString("tts_engine_type", "SYSTEM") == "SYSTEM"
                findPreference<PreferenceCategory>("tts_system_category")?.isVisible = isSystem
                findPreference<PreferenceCategory>("tts_legado_category")?.isVisible = !isSystem
            }
        }
    }
}
