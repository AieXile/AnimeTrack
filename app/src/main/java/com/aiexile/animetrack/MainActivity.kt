package com.aiexile.animetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.home.HomeScreen
import com.aiexile.animetrack.ui.settings.AboutScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory())
            val themeMode by themeViewModel.themeMode.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()
            
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            
            AnimeTrackTheme(darkTheme = darkTheme) {
                AnimeTrackApp(themeViewModel = themeViewModel)
            }
        }
    }
}

private val mainPages = listOf(
    MainPage("home", "首页"),
    MainPage("favorites", "收藏"),
    MainPage("timeline", "时间线"),
    MainPage("settings", "设置")
)

private data class MainPage(val route: String, val title: String)

sealed class Screen {
    data class Main(val pageIndex: Int) : Screen()
    data object About : Screen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeTrackApp(
    themeViewModel: ThemeViewModel
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main(0)) }
    val pagerState = rememberPagerState(pageCount = { mainPages.size })
    val scope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
            is Screen.Main -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondBoundsPageCount = 1
                    ) { page ->
                        when (mainPages[page].route) {
                            "home" -> HomeScreen(
                                themeViewModel = themeViewModel,
                                showBottomBar = false,
                                onNavigate = { }
                            )
                            "favorites" -> PlaceholderScreen(title = "收藏", showBottomBar = false)
                            "timeline" -> TimelineScreen(showBottomBar = false, onNavigate = { })
                            "settings" -> SettingsScreen(
                                showBottomBar = false,
                                onNavigateAbout = { currentScreen = Screen.About },
                                onNavigate = { }
                            )
                        }
                    }
                    
                    BottomNavigationBar(
                        currentRoute = mainPages[pagerState.currentPage].route,
                        onNavigate = { route ->
                            val targetIndex = mainPages.indexOfFirst { it.route == route }
                            if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                                scope.launch {
                                    pagerState.animateScrollToPage(targetIndex)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
            is Screen.About -> {
                AboutScreen(
                    onBack = { currentScreen = Screen.Main(3) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    showBottomBar: Boolean = true
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = title.lowercase(),
                    onNavigate = { }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$title 页面开发中...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
