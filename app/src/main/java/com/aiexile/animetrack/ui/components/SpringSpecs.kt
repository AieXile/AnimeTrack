package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec

/**
 * iOS 风格 Spring 动画参数预设（常量表）。
 *
 * 抽取自开源库 Compose-Symphony（MIT 协议）：
 * https://github.com/jay3-yy/Compose-Symphony
 *
 * 基于 iOS Human Interface Guidelines 的动画调参，按使用场景区分回弹感与响应速度。
 * 仅作为今后新增动画的参数规范使用，存量动画的既有参数不做替换，避免手感变化。
 */
object iOSSpringSpecs {

    /** 按钮点击反馈：快速响应，轻微回弹。适用于 IconButton、ActionButton 等 */
    val ButtonPress: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.6f,
        stiffness = 400f
    )

    /** 页面切换：临界阻尼无回弹，自然停止。适用于导航过渡、页面滑入滑出 */
    val PageTransition: SpringSpec<Float> = SpringSpec(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 卡片展开：适度回弹，有活力感。适用于卡片详情展开、BottomSheet 弹出 */
    val CardExpand: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.8f,
        stiffness = 300f
    )

    /** 侧边栏/抽屉：轻微回弹，快速响应。适用于 Sidebar、Drawer 展开收起 */
    val Drawer: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.7f,
        stiffness = 350f
    )

    /** 列表项入场：中等回弹，Q 弹效果。适用于 LazyColumn 卡片入场 */
    val ListItem: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.65f,
        stiffness = 300f
    )

    /** 刷新指示器：轻微回弹。适用于下拉刷新旋转动画 */
    val RefreshIndicator: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.7f,
        stiffness = 300f
    )

    /** 缩放变换通用参数：默认中等回弹 + 中等刚度 */
    val Scale: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
