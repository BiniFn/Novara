package org.skepsun.kototoro.core.parser.legado.bridge

import org.skepsun.kototoro.core.parser.legado.runtime.LegadoCookieStore
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoHttpExecutor
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoRuntimeLogger
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoVariableStore
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoWebViewFetcher

/**
 * Kototoro 侧的 Legado runtime bridge 占位入口。
 *
 * 当前仅用于收口后续实现名，避免在接线阶段继续把能力散落到 AnalyzeUrl / Repository / Sandbox。
 */
data class KototoroLegadoRuntimeBridge(
    val httpExecutor: LegadoHttpExecutor,
    val cookieStore: LegadoCookieStore,
    val variableStore: LegadoVariableStore,
    val webViewFetcher: LegadoWebViewFetcher? = null,
    val logger: LegadoRuntimeLogger,
)
