package com.aiexile.animetrack.data.auth

import android.os.Build
import com.aiexile.animetrack.di.AppContainer

/**
 * 登录设备信息（多端登录会话管理用）。
 * deviceId 复用活跃统计的 UUID（SettingsRepository），保证同一设备稳定；
 * deviceName 取厂商 + 型号，用于服务端设备列表展示。
 */
object DeviceInfo {

    /** 登录平台标识，与服务端 platform 字段一致 */
    const val PLATFORM = "android"

    /** 设备唯一标识（首次调用时生成 UUID 并持久化） */
    suspend fun getDeviceId(): String =
        AppContainer.getSettingsRepository().getOrCreateDeviceId()

    /** 设备名称：MODEL 已含品牌前缀（如 "MEIZU 21"）时只显示 MODEL，否则 "品牌 MODEL" */
    val deviceName: String
        get() {
            val model = Build.MODEL?.trim().takeUnless { it.isNullOrEmpty() } ?: "Android"
            val manufacturer = Build.MANUFACTURER?.trim().takeUnless { it.isNullOrEmpty() }
                ?: return model.take(64)
            // 厂商词首字母大写（meizu → Meizu），保持显示整洁
            val vendor = manufacturer.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$vendor $model"
            }.take(64)
        }
}
