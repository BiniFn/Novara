package org.skepsun.kototoro.core.ui.compose

import android.os.SystemClock
import android.util.Log

private const val HeroTransitionLogTag = "HeroTransition"

fun logHeroTransition(message: String) {
    Log.d(HeroTransitionLogTag, message)
}

fun heroTransitionTimestampMs(): Long = SystemClock.uptimeMillis()
