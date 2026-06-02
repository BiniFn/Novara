package org.skepsun.kototoro.core.ui.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

enum class HeroTransitionPhase {
    Idle,
    EnteringDetails,
    ReturningFromDetails,
}

val LocalHeroTransitionInProgress: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

val LocalHeroReturnTransitionInProgress: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

val LocalHeroTransitionPhase: ProvidableCompositionLocal<HeroTransitionPhase> =
    staticCompositionLocalOf { HeroTransitionPhase.Idle }
