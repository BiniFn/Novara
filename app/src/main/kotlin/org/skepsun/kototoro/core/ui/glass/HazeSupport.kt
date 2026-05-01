package org.skepsun.kototoro.core.ui.glass

import android.os.Build

internal fun supportsRuntimeHaze(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
