package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.TranslationE2ESettingsScreen
import org.skepsun.kototoro.settings.support.TranslationApiSettingsSupport
import javax.inject.Inject
import org.json.JSONObject
import org.json.JSONArray

@AndroidEntryPoint
class TranslationEndToEndApiSettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    @ContentHttpClient
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var appSettings: AppSettings

    private var fetchModelsJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KototoroTheme {
                    TranslationE2ESettingsScreen(
                        settings = appSettings,
                        onFetchModels = { fetchAndPickApiModel() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.reader_translation_e2e_api_settings_title))
        appSettings.subscribe(this)
    }

    override fun onDestroyView() {
        appSettings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppSettings.KEY_READER_E2E_API_PROVIDER_PRESET -> {
                val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                TranslationApiSettingsSupport.applyApiProviderPreset(
                    sharedPreferences = preferences,
                    presetInput = appSettings.readerE2eApiProviderPreset,
                    forceOverride = true,
                    endpointKey = AppSettings.KEY_READER_E2E_API_ENDPOINT,
                    modelKey = AppSettings.KEY_READER_E2E_API_MODEL,
                )
            }
        }
    }

    private fun fetchAndPickApiModel() {
        if (fetchModelsJob?.isActive == true) return

        val endpoint = appSettings.readerE2eApiEndpoint
        val apiKey = appSettings.readerE2eApiKey
        if (endpoint.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(requireContext(), R.string.reader_translation_api_endpoint_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val url = endpoint.removeSuffix("/chat/completions").removeSuffix("/") + "/models"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()

        fetchModelsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string()

                if (!response.isSuccessful || bodyStr == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val json = JSONObject(bodyStr)
                val dataArr = json.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<String>()
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.optJSONObject(i)
                    val id = obj?.optString("id")
                    if (!id.isNullOrBlank()) {
                        models.add(id)
                    }
                }

                models.sort()

                withContext(Dispatchers.Main) {
                    if (models.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.reader_translation_api_models_fetch)
                        .setItems(models.toTypedArray()) { _, which ->
                            val chosen = models[which]
                            appSettings.prefs.edit().putString(AppSettings.KEY_READER_E2E_API_MODEL, chosen).apply()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.reader_translation_api_models_fetch_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
