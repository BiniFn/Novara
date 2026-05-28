package org.skepsun.kototoro.core.parser.legado.bridge

import android.util.Log
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoJsEvaluator
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoRuleRuntimeContext
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox

/**
 * 将 LegadoSandbox 适配为最小 JS evaluator。
 */
class LegadoSandboxJsEvaluator(
    private val sandbox: LegadoSandbox?,
    private val runtimeContext: LegadoRuleRuntimeContext? = null,
    private val key: String?,
    private val page: Int,
    private val baseUrl: String,
) : LegadoJsEvaluator {

    override fun evaluate(script: String, result: Any?): Any? {
        runtimeContext?.putVariableAny("page", page)
        key?.let { runtimeContext?.putVariable("key", it) }
        runtimeContext?.putVariable("baseUrl", baseUrl)
        runtimeContext?.putVariableAny("result", result)
        runtimeContext?.putVariableAny("src", result)
        runtimeContext?.evalJs(script, result, baseUrl)?.let { return it }

        val activeSandbox = sandbox
        if (activeSandbox == null) {
            Log.w(TAG, "Sandbox not available for JS evaluation")
            return result
        }

        activeSandbox.putVariable("key", key)
        activeSandbox.putVariable("page", page.toString())
        activeSandbox.putVariable("baseUrl", baseUrl)
        activeSandbox.setResult(result)
        return activeSandbox.eval(script)
    }

    private companion object {
        private const val TAG = "LegadoSandboxJsEval"
    }
}
