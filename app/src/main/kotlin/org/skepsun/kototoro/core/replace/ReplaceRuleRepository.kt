package org.skepsun.kototoro.core.replace

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReplaceRuleRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("replace_rules", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    suspend fun getAll(): List<ReplaceRule> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_RULES, "[]") ?: "[]"
        runCatching { json.decodeFromString<List<ReplaceRule>>(raw) }
            .getOrDefault(emptyList())
            .sortedBy { it.order }
    }

    suspend fun getContentRules(): List<ReplaceRule> = withContext(Dispatchers.IO) {
        getAll().filter { it.isEnabled && it.isValid() && (it.scopeContent || it.scope == ReplaceRule.Scope.BOTH) }
    }

    suspend fun getTitleRules(): List<ReplaceRule> = withContext(Dispatchers.IO) {
        getAll().filter { it.isEnabled && it.isValid() && (it.scopeTitle || it.scope == ReplaceRule.Scope.BOTH) }
    }

    suspend fun saveAll(rules: List<ReplaceRule>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_RULES, json.encodeToString(rules)).apply()
    }

    suspend fun importFromJson(importJson: String): Int = withContext(Dispatchers.IO) {
        val imported = runCatching {
            json.decodeFromString<List<ReplaceRule>>(importJson)
        }.getOrNull() ?: return@withContext 0

        val existing = getAll()
        val existingIds = existing.map { it.id }.toSet()

        val newRules = imported.filter { it.pattern.isNotBlank() }
        val merged = existing.toMutableList()

        newRules.forEach { importedRule ->
            val idx = existingIds.indexOf(importedRule.id)
            if (idx >= 0) {
                merged[idx] = importedRule
            } else {
                merged.add(importedRule)
            }
        }

        prefs.edit().putString(KEY_RULES, json.encodeToString(merged.filter { it.pattern.isNotBlank() })).apply()

        merged.size - existing.size
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        json.encodeToString(getAll())
    }

    companion object {
        private const val KEY_RULES = "rules"
    }
}
