package com.aiexile.animetrack.data.websource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiexile.animetrack.model.WebSourceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.webSourceRuleStore: DataStore<Preferences> by
    preferencesDataStore(name = "web_source_rules")

/**
 * 用户导入的数据源规则。
 *
 * 规则是低频、小体量的结构化配置，沿用 DataStore + JSON 的存储方式，
 * 不为它单独开 Room 表。同名规则视为同一条，导入时原位覆盖。
 */
class WebSourceRuleRepository(private val context: Context) {

    val rules: Flow<List<WebSourceRule>> = context.webSourceRuleStore.data
        .onStart { ensureDefaultRules() }
        .map { prefs -> decode(prefs[KEY_RULES].orEmpty()) }
        .distinctUntilChanged()

    val selectedName: Flow<String?> = context.webSourceRuleStore.data
        .map { prefs -> prefs[KEY_SELECTED]?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()

    suspend fun importRules(incoming: List<WebSourceRule>): Int {
        if (incoming.isEmpty()) return 0
        ensureDefaultRules()
        context.webSourceRuleStore.edit { prefs ->
            val merged = LinkedHashMap<String, WebSourceRule>()
            decode(prefs[KEY_RULES].orEmpty()).forEach { merged[it.name] = it }
            incoming.forEach { merged[it.name] = it }
            prefs[KEY_RULES] = WebSourceRuleCodec.encode(merged.values.toList())
        }
        return incoming.size
    }

    suspend fun delete(name: String) {
        ensureDefaultRules()
        context.webSourceRuleStore.edit { prefs ->
            val remaining = decode(prefs[KEY_RULES].orEmpty()).filterNot { it.name == name }
            if (remaining.isEmpty()) {
                prefs.remove(KEY_RULES)
            } else {
                prefs[KEY_RULES] = WebSourceRuleCodec.encode(remaining)
            }
            if (prefs[KEY_SELECTED] == name) prefs.remove(KEY_SELECTED)
        }
    }

    suspend fun select(name: String?) {
        context.webSourceRuleStore.edit { prefs ->
            if (name.isNullOrBlank()) {
                prefs.remove(KEY_SELECTED)
            } else {
                prefs[KEY_SELECTED] = name
            }
        }
    }

    private fun decode(raw: String): List<WebSourceRule> =
        WebSourceRuleCodec.decode(raw).getOrNull()?.rules.orEmpty()

    /** 升级和首次使用时补入一次；保留同名自定义规则，用户删除后不再自动恢复。 */
    private suspend fun ensureDefaultRules() {
        if (context.webSourceRuleStore.data.first()[KEY_DEFAULTS_INSTALLED] == true) return
        val defaults = withContext(Dispatchers.IO) {
            context.assets.open("websource/default_rules.json").bufferedReader().use { reader ->
                WebSourceRuleCodec.decode(reader.readText()).getOrThrow().rules
            }
        }
        context.webSourceRuleStore.edit { prefs ->
            if (prefs[KEY_DEFAULTS_INSTALLED] == true) return@edit
            val merged = LinkedHashMap<String, WebSourceRule>()
            defaults.forEach { merged[it.name] = it }
            decode(prefs[KEY_RULES].orEmpty()).forEach { merged[it.name] = it }
            prefs[KEY_RULES] = WebSourceRuleCodec.encode(merged.values.toList())
            if (prefs[KEY_SELECTED].isNullOrBlank()) {
                merged.keys.firstOrNull()?.let { prefs[KEY_SELECTED] = it }
            }
            prefs[KEY_DEFAULTS_INSTALLED] = true
        }
    }

    private companion object {
        val KEY_RULES = stringPreferencesKey("rules")
        val KEY_SELECTED = stringPreferencesKey("selected")
        // v3 起内置 API 规则站：已有用户在下一次启动时补入新规则，
        // 同名自定义规则仍然优先；用户手动删除的默认规则会在这次升级中被补回一次
        val KEY_DEFAULTS_INSTALLED = booleanPreferencesKey("defaults_installed_v3")
    }
}
