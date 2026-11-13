package com.aiexile.animetrack.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

/**
 * 三档材质共用真实用户热路径；准备工作在 collect 外执行，不把批量添加测试数据录入 Profile。
 * 运行 ./gradlew :app:generateReleaseBaselineProfile，产物由插件合并到 release 源集。
 * 只操作独立 .benchmark 包，不读取或修改正式版数据。
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        val journey = BaselineUiJourney(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
        journey.prepare("STANDARD")
        rule.collect(
            packageName = BaselineUiJourney.PACKAGE_NAME,
            maxIterations = 8,
            stableIterations = 3,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun standard() = collectStyle("STANDARD")

    @Test
    fun haze() = collectStyle("HAZE")

    @Test
    fun liquidGlass() = collectStyle("LIQUID_GLASS")

    private fun collectStyle(style: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val journey = BaselineUiJourney(device)
        journey.prepare(style)
        rule.collect(
            packageName = BaselineUiJourney.PACKAGE_NAME,
            maxIterations = 8,
            stableIterations = 3,
            includeInStartupProfile = false,
        ) {
            journey.runtimeMark("iteration: pressHome")
            pressHome()
            journey.runtimeMark("iteration: startActivityAndWait")
            startActivityAndWait()
            journey.runtimeMark("iteration: activity ready")
            journey.runHotPaths(style)
        }
    }
}
