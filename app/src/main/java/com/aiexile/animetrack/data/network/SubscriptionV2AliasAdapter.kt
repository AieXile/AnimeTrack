package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.data.log.AppLogManager
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * 订阅 alias 的兼容反序列化：同时接受规格格式与旧版服务端格式。
 *
 * - 规格格式：`{"type":"bangumi","value":"123"}`
 * - 旧版格式：`"3"` / `"3ccdfa65-95ef-47ee-96b2-0158006c41fe"`（原始键字符串）
 *
 * 旧格式丢了类型信息，只能按值的形态保守推断：
 * - 纯数字 → legacy_local：本地自增 id 与 Bangumi ID 可能同值，规则层还要求标题一致才采信；
 * - UUID → remote_sync：本地自增 id 永远不是 UUID，精确匹配不存在撞车风险；
 * - 其他形态 → 未知类型，直接跳过（宁可漏删一张卡，也不能误删别的订阅）。
 *
 * 单独成文件的原因：这是对外接口脏数据的兜底解析，不应混进业务合并逻辑。
 */
class SubscriptionV2AliasAdapter : TypeAdapter<SubscriptionV2Alias>() {

    override fun write(out: JsonWriter, value: SubscriptionV2Alias?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name(FIELD_TYPE).value(value.type)
        out.name(FIELD_VALUE).value(value.value)
        out.endObject()
    }

    override fun read(input: JsonReader): SubscriptionV2Alias = when (input.peek()) {
        JsonToken.NULL -> {
            input.nextNull()
            IGNORED
        }
        JsonToken.BEGIN_OBJECT -> readTyped(input)
        JsonToken.STRING, JsonToken.NUMBER -> readLegacy(input.nextString())
        else -> {
            input.skipValue()
            IGNORED
        }
    }

    private fun readTyped(input: JsonReader): SubscriptionV2Alias {
        var type: String? = null
        var value: String? = null
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                FIELD_TYPE -> type = input.nextStringOrNull()
                FIELD_VALUE -> value = input.nextStringOrNull()
                else -> input.skipValue()
            }
        }
        input.endObject()
        val aliasType = type?.trim().orEmpty()
        val aliasValue = value?.trim().orEmpty()
        return if (aliasType.isEmpty() || aliasValue.isEmpty()) {
            IGNORED
        } else {
            SubscriptionV2Alias(aliasType, aliasValue)
        }
    }

    private fun readLegacy(raw: String): SubscriptionV2Alias {
        val key = raw.trim()
        if (key.isEmpty()) return IGNORED
        warnLegacyFormatOnce()
        if (key.toIntOrNull() != null) {
            return SubscriptionV2Alias(SubscriptionV2Alias.TYPE_LEGACY_LOCAL, key)
        }
        if (UUID_PATTERN.matches(key)) {
            return SubscriptionV2Alias(SubscriptionV2Alias.TYPE_REMOTE_SYNC, key)
        }
        return IGNORED
    }

    /** 旧格式是服务端契约漂移，记录一次便于排查（每进程仅一条，避免同步时刷屏） */
    private fun warnLegacyFormatOnce() {
        if (legacyFormatWarned) return
        legacyFormatWarned = true
        AppLogManager.w(
            TAG,
            "服务端订阅 alias 仍为旧字符串格式，已按值形态保守推断（建议服务端升级为 {type,value}）"
        )
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString()
        }

    companion object {
        private const val TAG = "SubscriptionApi"
        private const val FIELD_TYPE = "type"
        private const val FIELD_VALUE = "value"

        /** 匹配不了任何类型分支的占位别名；避免列表里出现 null 元素导致调用方空指针 */
        private val IGNORED = SubscriptionV2Alias(SubscriptionV2Alias.TYPE_UNKNOWN, "")

        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        @Volatile
        private var legacyFormatWarned = false
    }
}
