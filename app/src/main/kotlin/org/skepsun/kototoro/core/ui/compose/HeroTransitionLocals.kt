package org.skepsun.kototoro.core.ui.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHeroTransitionInProgress: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
