package com.aiexile.animetrack.localmedialab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiexile.animetrack.ui.player.PlayerScreen
import com.aiexile.animetrack.ui.player.local.LocalMediaLibraryScreen

/** 真机验收入口，仅 debug 包：直接使用生产本地库和播放器，不读取正式包数据。 */
class LocalMediaLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "library") {
                    composable("library") {
                        LocalMediaLibraryScreen(onBack = ::finish, onPlayMedia = { nav.navigate("play/$it") })
                    }
                    composable("play/{mediaId}", listOf(navArgument("mediaId") { type = NavType.LongType })) { entry ->
                        PlayerScreen(
                            animeId = 0,
                            initialLocalMediaId = entry.arguments?.getLong("mediaId"),
                            onBack = { nav.popBackStack() },
                            onBrowseWebDAV = {}
                        )
                    }
                }
            }
        }
    }
}
