package com.aiexile.animetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.home.HomeScreen
import com.aiexile.animetrack.ui.settings.AboutScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.ui.theme.ThemeViewModel

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
                AnimeTrackNavHost(themeViewModel = themeViewModel)
            }
        }
    }
}

private val mainRoutes = listOf("home", "favorites", "timeline", "settings")

private fun getRouteIndex(route: String?): Int {
    if (route == null) return -1
    return mainRoutes.indexOf(route).takeIf { it >= 0 } ?: Int.MAX_VALUE
}

private fun isMainRoute(route: String?): Boolean {
    return route in mainRoutes
}

private const val ANIM_DURATION = 300

@Composable
fun AnimeTrackNavHost(
    themeViewModel: ThemeViewModel,
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = {
                if (isMainRoute(initialState.destination.route) && isMainRoute(targetState.destination.route)) {
                    val initialIndex = getRouteIndex(initialState.destination.route)
                    val targetIndex = getRouteIndex(targetState.destination.route)
                    
                    if (targetIndex > initialIndex) {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION))
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION))
                    }
                } else {
                    fadeIn(animationSpec = tween(ANIM_DURATION))
                }
            },
            exitTransition = {
                if (isMainRoute(initialState.destination.route) && isMainRoute(targetState.destination.route)) {
                    val initialIndex = getRouteIndex(initialState.destination.route)
                    val targetIndex = getRouteIndex(targetState.destination.route)
                    
                    if (targetIndex > initialIndex) {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION))
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION))
                    }
                } else {
                    fadeOut(animationSpec = tween(ANIM_DURATION))
                }
            },
            popEnterTransition = {
                if (isMainRoute(initialState.destination.route) && isMainRoute(targetState.destination.route)) {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION))
                } else {
                    fadeIn(animationSpec = tween(ANIM_DURATION))
                }
            },
            popExitTransition = {
                if (isMainRoute(initialState.destination.route) && isMainRoute(targetState.destination.route)) {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION))
                } else {
                    fadeOut(animationSpec = tween(ANIM_DURATION))
                }
            }
        ) {
            composable("home") {
                HomeScreen(
                    themeViewModel = themeViewModel,
                    showBottomBar = true,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("favorites") {
                PlaceholderScreen(
                    title = "收藏",
                    showBottomBar = true
                )
            }
            composable("timeline") {
                TimelineScreen(
                    showBottomBar = true,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    showBottomBar = true,
                    onNavigateAbout = { navController.navigate("about") },
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("about") {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
        
        if (isMainRoute(currentRoute)) {
            BottomNavigationBar(
                currentRoute = currentRoute ?: "home",
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
