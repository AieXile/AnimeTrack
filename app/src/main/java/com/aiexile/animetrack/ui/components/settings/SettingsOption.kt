package com.aiexile.animetrack.ui.components.settings

import androidx.compose.runtime.Immutable

/**
 * 设置项选项弹层里的一个候选项。
 *
 * 行与菜单共用同一模型：行侧只取 [label] 显示当前值，菜单侧渲染 [label] +
 * [supportingText]。业务值（枚举 / Float / Int）放在 [value] 中，组件不感知类型，
 * 也不负责持久化——选中后只回调一次，由页面写回自己的仓库。
 *
 * @param value 业务值，调用方用于比较与写回
 * @param label 候选项文案（行内当前值也用它）
 * @param supportingText 可选副文案，例如「HTTP/HTTPS CONNECT 隧道」这类选择依据
 * @param enabled 该候选项是否可选；false 时整行 38% 透明度且不可点
 */
@Immutable
data class SettingsOption<T>(
    val value: T,
    val label: String,
    val supportingText: String? = null,
    val enabled: Boolean = true,
)
