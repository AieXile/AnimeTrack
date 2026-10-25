package com.aiexile.animetrack.navigation

import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 详情路由参数解析契约测试。
 *
 * 背景：Navigation 只有在路由模板里声明了 query 占位符（`?arg={arg}`）时才会解析 query，
 * 否则整段 query 会被丢弃。收藏页依赖 `sharedKeyPrefix` 与详情页配对共享元素 key，
 * 这里锁定该行为，避免回归成"收藏页不飞"。
 */
@RunWith(AndroidJUnit4::class)
class DetailRouteArgsTest {

    /** NavController 建立图涉及 Lifecycle，必须在主线程执行 */
    private fun onMainThread(block: (NavHostController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context: Context = instrumentation.targetContext
            val controller = NavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                setGraph(
                    createGraph(startDestination = "start") {
                        composable("start") {}
                        composable(Routes.ANIME_DETAIL, arguments = Routes.animeDetailArguments) {}
                    },
                    null
                )
            }
            block(controller)
        }
    }

    @Test
    fun sharedKeyPrefix_parsedFromQuery() {
        var prefix: String? = null
        var animeId = 0
        onMainThread { controller ->
            controller.navigate(
                Routes.animeDetail(
                    animeId = 5,
                    coverUrl = "https://example.com/cover.jpg",
                    sharedKeyPrefix = "favorites_"
                )
            )
            val args = controller.currentBackStackEntry?.arguments
            prefix = args?.getString("sharedKeyPrefix")
            animeId = args?.getInt("animeId") ?: 0
        }
        assertEquals("favorites_", prefix)
        assertEquals(5, animeId)
    }

    @Test
    fun omittedPrefix_navigatesWithNullPrefix() {
        var prefix: String? = "unset"
        onMainThread { controller ->
            controller.navigate(Routes.animeDetail(animeId = 5, coverUrl = null))
            prefix = controller.currentBackStackEntry?.arguments?.getString("sharedKeyPrefix")
        }
        assertNull(prefix)
    }
}
