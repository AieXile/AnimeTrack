package com.aiexile.animetrack.ui.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDavPlaybackNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Test
    fun fromPlaybackSettingsOpensPlayerWithSelectedFile() {
        mountGraph()
        compose.onNodeWithText("从设置浏览").performClick()
        val browser = compose.runOnIdle { checkNotNull(nav.currentBackStackEntry) }
        compose.onNodeWithText(FILE_NAME).performClick()
        compose.onNodeWithText("播放器 0").assertIsDisplayed()
        compose.runOnIdle {
            assertSelectionOnPlayer()
            assertSame(browser, nav.previousBackStackEntry)
            assertNull(browser.savedStateHandle.get<Bundle>(WEB_DAV_SELECTION_KEY))
            nav.popBackStack()
        }
        compose.onNodeWithText(FILE_NAME).assertIsDisplayed()
    }

    @Test
    fun fromAnimeKeepsAnimeContextAndReturnsToBrowser() {
        mountGraph()
        compose.onNodeWithText("从番剧浏览").performClick()
        val browser = compose.runOnIdle { checkNotNull(nav.currentBackStackEntry) }
        compose.onNodeWithText(FILE_NAME).performClick()
        compose.onNodeWithText("播放器 42").assertIsDisplayed()
        compose.runOnIdle {
            assertSelectionOnPlayer()
            assertSame(browser, nav.previousBackStackEntry)
            assertEquals(true, nav.currentBackStackEntry?.arguments?.getBoolean("webDavPlay"))
            nav.popBackStack()
        }
        compose.onNodeWithText(FILE_NAME).assertIsDisplayed()
    }

    private fun assertSelectionOnPlayer() {
        val player = checkNotNull(nav.currentBackStackEntry)
        assertEquals(Routes.PLAYER, player.destination.route)
        val selection = player.savedStateHandle.get<Bundle>(WEB_DAV_SELECTION_KEY)
        assertNotNull(selection)
        assertEquals(FILE_URL, selection?.getString(WEB_DAV_SELECTION_PATH))
        assertEquals(FILE_NAME, selection?.getString(WEB_DAV_SELECTION_NAME))
    }

    private fun mountGraph() = compose.setContent {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.PLAYER_SETTINGS) {
            composable(Routes.PLAYER_SETTINGS) {
                Column {
                    Button(onClick = { nav.navigate(Routes.webDavBrowse()) }) { Text("从设置浏览") }
                    Button(onClick = { nav.navigate(Routes.webDavBrowse(42)) }) { Text("从番剧浏览") }
                }
            }
            composable(Routes.PLAYER, arguments = Routes.playerArguments) { entry ->
                Text("播放器 ${entry.arguments?.getInt("animeId")}")
            }
            composable(Routes.WEBDAV_BROWSE, arguments = Routes.webDavBrowseArguments) { entry ->
                Button(onClick = { nav.playWebDavSelection(entry, FILE_URL, FILE_NAME) }) { Text(FILE_NAME) }
            }
        }
    }

    private companion object {
        const val FILE_URL = "https://example.invalid/dav/%E7%AC%AC%201%20%E9%9B%86.mp4"
        const val FILE_NAME = "第 1 集.mp4"
    }
}
