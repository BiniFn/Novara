package org.skepsun.kototoro.core.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun <T> AppSettings.observeAsState(
    key: String,
    selector: AppSettings.() -> T
): State<T> {
    return observeAsState(*arrayOf(key), selector = selector)
}

@Composable
fun <T> AppSettings.observeAsState(
    vararg keys: String,
    selector: AppSettings.() -> T
): State<T> {
    val state = remember { mutableStateOf(selector()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, *keys) {
        fun refresh() {
            val value = selector()
            if (state.value != value) {
                state.value = value
            }
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey in keys) {
                refresh()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        this@observeAsState.subscribe(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            this@observeAsState.unsubscribe(listener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return state
}
