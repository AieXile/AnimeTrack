package com.aiexile.animetrack.baselineprofile

import android.graphics.Point
import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test

/**
 * Baseline Profile 生成器。
 *
 * 录制真实用户热路径，使安装后热路径 AOT 预编译，消除首屏 JIT 锁竞争（对应 benchmark 报告 P1）。
 *
 * 交互序列：
 *  1. 冷启动
 *  2. 向下 1 次 fling、向上 1 次 fling
 *  3. 点击一张卡片 → 退出
 *  4. 底部导航点「设置」→ 回到主页
 *  5. 底部导航点「看板」→ 回到主页
 *  6. 再向下 1 次 fling、向上 1 次 fling
 *  7. 点击一张卡片 → 退出
 *
 * 生成命令（需连接真机或模拟器）：
 *   ./gradlew :app:generateReleaseBaselineProfile
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = PACKAGE_NAME) {
            // 1. 冷启动：捕获 Application/Activity/首帧渲染热路径
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            SystemClock.sleep(1200)

            // 2. 向下 1 次 + 向上 1 次
            flingOnceDownThenUp(device)

            // 3. 点击一张卡片 → 退出
            tapFirstCardAndBack(device)

            // 4. 点「设置」→ 回主页
            tapBottomNav(device, NAV_SETTINGS)
            goBackHome(device)

            // 5. 点「看板」→ 回主页
            tapBottomNav(device, NAV_SCHEDULE)
            goBackHome(device)

            // 6. 再向下 1 次 + 向上 1 次
            flingOnceDownThenUp(device)

            // 7. 点击一张卡片 → 退出
            tapFirstCardAndBack(device)
        }
    }

    // ---- 交互动作 ----

    /** 主页网格向下 1 次 fling、向上 1 次 fling */
    private fun flingOnceDownThenUp(device: UiDevice) {
        val content = device.findObject(By.scrollable(true)) ?: return
        content.setGestureMargin(device.displayWidth / 5)
        content.fling(Direction.DOWN)
        device.waitForIdle()
        content.fling(Direction.UP)
        device.waitForIdle()
    }

    /** 点击首张卡片进入详情，再退出 */
    private fun tapFirstCardAndBack(device: UiDevice) {
        val center = findFirstCardCenter(device) ?: return
        device.click(center.x, center.y)
        device.waitForIdle()
        SystemClock.sleep(600)
        device.pressBack()
        device.waitForIdle()
    }

    /** 点击底部导航项 */
    private fun tapBottomNav(device: UiDevice, navItem: NavItem) {
        val node = findBottomNav(device, navItem) ?: return
        node.click()
        device.waitForIdle()
        SystemClock.sleep(500)
    }

    /** 返回主页：优先点底部导航「主页」，找不到则按返回键兜底 */
    private fun goBackHome(device: UiDevice) {
        val home = findBottomNav(device, NAV_HOME)
        if (home != null) {
            home.click()
        } else {
            device.pressBack()
        }
        device.waitForIdle()
        SystemClock.sleep(500)
    }

    // ---- 查找 ----

    /**
     * 通过底部导航图标 contentDescription 定位（文案随语言变化，做精确匹配 + 包含匹配兜底）。
     */
    private fun findBottomNav(device: UiDevice, navItem: NavItem): UiObject2? {
        // 先按完整 contentDescription 匹配
        for (desc in navItem.candidates) {
            device.findObject(By.desc(desc))?.let { return it }
        }
        // 再按包含关键字匹配（兼容语言变体）
        for (desc in navItem.candidates) {
            device.findObject(By.descContains(desc))?.let { return it }
        }
        return null
    }

    /**
     * 定位主页首屏第一张卡片的中心坐标。
     *
     * LazyVerticalGrid 首个 item 是 header（头像 + 横幅 + 筛选菜单）。
     * 此前返回 UiObject2，fling 后节点失效会抛 StaleObjectException；
     * 且只检测头像 desc，漏判 header 时点击中心会落在筛选按钮上。
     *
     * 修复：
     * 1. 返回坐标而非 UiObject2，规避 StaleObjectException。
     * 2. header 检测覆盖头像/登录/筛选三类 desc（三语言），命中任一即跳过。
     * 3. 兜底：所有 child 都被判为 header 或未命中时，跳过第一个 child 取第二个。
     */
    private fun findFirstCardCenter(device: UiDevice): Point? {
        val content = device.findObject(By.scrollable(true)) ?: return null
        val children = content.children
        for (child in children) {
            val isHeader = HEADER_DESCS.any { desc ->
                child.findObject(By.descContains(desc)) != null
            }
            if (isHeader) continue
            val bounds = child.visibleBounds
            return Point(bounds.centerX(), bounds.centerY())
        }
        // 兜底：跳过第一个 child（header 本身仍存在）
        if (children.size > 1) {
            val bounds = children[1].visibleBounds
            return Point(bounds.centerX(), bounds.centerY())
        }
        return null
    }

    companion object {
        private const val PACKAGE_NAME = "com.aiexile.animetrack"

        // LazyVerticalGrid header item 内出现的 contentDescription（三语言）
        // 用于跳过 header，避免点击落到头像/登录按钮/筛选按钮上
        //   home_user_avatar: 用户头像 / 使用者頭像 / User avatar
        //   home_login:        登录 / 登入 / Login
        //   home_filter:        筛选 / 篩選 / Filter
        private val HEADER_DESCS = listOf(
            "用户头像", "使用者頭像", "User avatar",
            "登录", "登入", "Login",
            "筛选", "篩選", "Filter"
        )

        // 依据 app/src/main/res 各语言 values 实测文案：
        //   nav_home: 首页 / 首頁 / Home
        //   bottom_nav_schedule: 看板 / 看板 / Schedule
        //   nav_settings: 设置 / 設定 / Settings
        private val NAV_HOME = NavItem("首页", "首頁", "Home")
        private val NAV_SCHEDULE = NavItem("看板", "Schedule")
        private val NAV_SETTINGS = NavItem("设置", "設定", "Settings")

        /** 一个导航项在多种语言下的 contentDescription 候选 */
        private data class NavItem(val candidates: List<String>) {
            constructor(vararg c: String) : this(c.toList())
        }
    }
}
