package org.skepsun.kototoro.core.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun <T> AppSettings.observeAsState(
    key: String,
    selector: AppSettings.() -> T
): State<T> {
    val state = remember { mutableStateOf(selector()) }
    DisposableEffect(key) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = selector()
            }
        }
        this@observeAsState.subscribe(listener)
        onDispose {
            this@observeAsState.unsubscribe(listener)
        }
    }
    return state
}
