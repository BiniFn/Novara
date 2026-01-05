package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen

/**
 * A source that has a configuration screen.
 */
interface ConfigurableAnimeSource : AnimeSource {
    /**
     * Set up the preference screen for this source.
     * @param screen The preference screen to add preferences to.
     */
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
