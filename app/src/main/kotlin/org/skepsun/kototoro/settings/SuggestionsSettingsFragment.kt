package org.skepsun.kototoro.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.SuggestionsSettingsScreen
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import org.skepsun.kototoro.suggestions.ui.SuggestionsWorker
import javax.inject.Inject

@AndroidEntryPoint
class SuggestionsSettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var repository: SuggestionRepository

    @Inject
    lateinit var suggestionsScheduler: SuggestionsWorker.Scheduler

    private val excludeTagsFlow = MutableStateFlow("")
    private val preferredTagsFlow = MutableStateFlow("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        refreshTags()
        return ComposeView(requireContext()).apply {
            setContent {
                KototoroTheme {
                    SuggestionsSettingsRoute(
                        settings = appSettings,
                        suggestionsScheduler = suggestionsScheduler,
                        excludeTagsFlow = excludeTagsFlow,
                        preferredTagsFlow = preferredTagsFlow,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.suggestions))
        appSettings.subscribe(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appSettings.unsubscribe(this)
    }

    private fun refreshTags() {
        excludeTagsFlow.value = appSettings.prefs.getString(AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS, "") ?: ""
        preferredTagsFlow.value = appSettings.prefs.getString(AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS, "") ?: ""
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS || key == AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS) {
            refreshTags()
        }
        if (appSettings.isSuggestionsEnabled && (key == AppSettings.KEY_SUGGESTIONS
                || key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS
                || key == AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS
                || key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW)
        ) {
            updateSuggestions()
        }
    }

    private fun updateSuggestions() {
        lifecycleScope.launch(Dispatchers.Default) {
            suggestionsScheduler.startNow()
        }
    }
}

@Composable
fun SuggestionsSettingsRoute(
    settings: AppSettings,
    suggestionsScheduler: SuggestionsWorker.Scheduler,
    excludeTagsFlow: MutableStateFlow<String>,
    preferredTagsFlow: MutableStateFlow<String>,
) {
    val coroutineScope = rememberCoroutineScope()
    val listener = remember(settings, suggestionsScheduler, coroutineScope) {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS || key == AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS) {
                excludeTagsFlow.value = settings.prefs.getString(AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS, "") ?: ""
                preferredTagsFlow.value = settings.prefs.getString(AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS, "") ?: ""
            }
            if (settings.isSuggestionsEnabled && (
                    key == AppSettings.KEY_SUGGESTIONS ||
                        key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS ||
                        key == AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS ||
                        key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW
                    )
            ) {
                coroutineScope.launch(Dispatchers.Default) {
                    suggestionsScheduler.startNow()
                }
            }
        }
    }
    DisposableEffect(settings, listener) {
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val excludeTags by excludeTagsFlow.collectAsState()
    val preferredTags by preferredTagsFlow.collectAsState()
    SuggestionsSettingsScreen(
        settings = settings,
        excludeTags = excludeTags,
        preferredTags = preferredTags,
        onExcludeTagsChanged = { value ->
            settings.prefs.edit().putString(AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS, value).apply()
        },
        onPreferredTagsChanged = { value ->
            settings.prefs.edit().putString(AppSettings.KEY_SUGGESTIONS_PREFERRED_TAGS, value).apply()
        },
    )
}
