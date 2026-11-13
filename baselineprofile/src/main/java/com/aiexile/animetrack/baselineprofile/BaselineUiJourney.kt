package com.aiexile.animetrack.baselineprofile

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * 采集专用 UI 驱动。
 *
 * 交互使用系统输入事件（`input swipe/tap`）而不是 UiAutomator 节点查询：
 * 本项目的采集设备（ColorOS）上节点查询会阻塞 UiAutomation，导致录制停住。
 * 点击位置按屏幕比例计算，首启/数据/材质均由采集包自身（BENCHMARK_MODE）处理。
 */
internal class BaselineUiJourney(private val device: UiDevice) {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext: Context by lazy { testContext.createPackageContext(PACKAGE_NAME, 0) }
    private var phase = "prepare"

    private val width = device.displayWidth
    private val height = device.displayHeight

    fun prepare(style: String) {
        check(android.os.Build.VERSION.SDK_INT >= 33) {
            "三档材质自动采集要求 Android 13+，不使用需要 root 的回退路径"
        }
        check(PACKAGE_NAME.endsWith(".benchmark"))
        phase = "$style/setup"
        device.wakeUp()
        // 启动前先核对安装包；绝不对正式包执行 force-stop / clear / uninstall。
        appContext.packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        mark("launching benchmark app with $style")
        launchFresh(style)
        // 采集包自行写入测试数据；这里只按固定时长等待首屏与数据落库。
        SystemClock.sleep(4_000)
        mark("$style prepared")
    }

    fun runtimeMark(message: String) = mark(message)

    fun runHotPaths(style: String) {
        phase = "$style/hotpaths"
        mark("grid fling down/up")
        swipe(0.5f, 0.72f, 0.5f, 0.30f, 120)
        SystemClock.sleep(600)
        swipe(0.5f, 0.30f, 0.5f, 0.72f, 120)
        SystemClock.sleep(600)
        swipe(0.5f, 0.80f, 0.5f, 0.20f, 90)
        SystemClock.sleep(600)
        swipe(0.5f, 0.20f, 0.5f, 0.80f, 90)
        SystemClock.sleep(600)

        mark("open detail and back")
        tap(0.18f, 0.26f)
        SystemClock.sleep(1_200)
        swipe(0.5f, 0.75f, 0.5f, 0.35f, 300)
        SystemClock.sleep(500)
        swipe(0.5f, 0.35f, 0.5f, 0.75f, 300)
        SystemClock.sleep(500)
        device.executeShellCommand("input keyevent 4")
        SystemClock.sleep(900)

        mark("pager swipe")
        swipe(0.20f, 0.94f, 0.63f, 0.94f, 300)
        SystemClock.sleep(900)
        tap(0.18f, 0.94f)
        SystemClock.sleep(700)

        mark("settings page")
        tap(0.63f, 0.94f)
        SystemClock.sleep(900)
        swipe(0.5f, 0.75f, 0.5f, 0.35f, 300)
        SystemClock.sleep(500)
        swipe(0.5f, 0.35f, 0.5f, 0.75f, 300)
        SystemClock.sleep(500)

        mark("schedule page")
        tap(0.48f, 0.94f)
        SystemClock.sleep(900)
        swipe(0.5f, 0.75f, 0.5f, 0.35f, 300)
        SystemClock.sleep(500)
        swipe(0.5f, 0.35f, 0.5f, 0.75f, 300)
        SystemClock.sleep(500)

        mark("timeline page")
        tap(0.33f, 0.94f)
        SystemClock.sleep(900)
        swipe(0.5f, 0.75f, 0.5f, 0.35f, 300)
        SystemClock.sleep(500)
        swipe(0.5f, 0.35f, 0.5f, 0.75f, 300)
        SystemClock.sleep(500)

        mark("back home")
        tap(0.18f, 0.94f)
        SystemClock.sleep(700)
        mark("$style hot paths complete")
    }

    private fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Int) {
        device.executeShellCommand(
            "input swipe ${px(fromX)} ${py(fromY)} ${px(toX)} ${py(toY)} $durationMs"
        )
    }

    private fun tap(fractionX: Float, fractionY: Float) {
        device.executeShellCommand("input tap ${px(fractionX)} ${py(fractionY)}")
    }

    private fun px(fraction: Float) = (width * fraction).toInt()
    private fun py(fraction: Float) = (height * fraction).toInt()

    private fun launchFresh(style: String) {
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
        // 不用 -W：部分 ROM 上等待首帧回报会长时间不返回；改为固定等待。
        device.executeShellCommand(
            "am start -n $PACKAGE_NAME/com.aiexile.animetrack.MainActivity " +
                "--es benchmark_style $style"
        )
        SystemClock.sleep(3_000)
    }

    private fun mark(message: String) = Log.i("BaselineJourney", "$phase: $message")

    private fun failMessage(reason: String): String {
        val directory = File(testContext.getExternalFilesDir(null), "baseline-failures").apply { mkdirs() }
        val filename = phase.replace('/', '_')
        runCatching { device.takeScreenshot(File(directory, "$filename.png")) }
        return "[$PACKAGE_NAME][$phase] $reason; diagnostics=$directory"
    }

    companion object {
        const val PACKAGE_NAME = "com.aiexile.animetrack.benchmark"
    }
}
