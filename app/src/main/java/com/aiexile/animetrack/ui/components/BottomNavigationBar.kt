package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.NavigationLabelMode
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

sealed class BottomNavItem(
    val route: String,
    @param:StringRes val titleRes: Int,
    /** 未选中图标（FILL=0 描边风格） */
    @param:DrawableRes val iconRes: Int,
    /** 选中图标（FILL=1 填充风格） */
    @param:DrawableRes val selectedIconRes: Int
) {
    object Home : BottomNavItem(
        route = "home",
        titleRes = R.string.nav_home,
        iconRes = R.drawable.sym_home,
        selectedIconRes = R.drawable.sym_fill_home
    )

    object Favorites : BottomNavItem(
        route = "favorites",
        titleRes = R.string.bottom_nav_favorites,
        iconRes = R.drawable.sym_collections_bookmark,
        selectedIconRes = R.drawable.sym_fill_collections_bookmark
    )

    object Timeline : BottomNavItem(
        route = "timeline",
        titleRes = R.string.nav_timeline,
        iconRes = R.drawable.sym_calendar_view_day,
        selectedIconRes = R.drawable.sym_fill_calendar_view_day
    )

    object Schedule : BottomNavItem(
        route = "schedule",
        titleRes = R.string.bottom_nav_schedule,
        iconRes = R.drawable.sym_calendar_clock,
        selectedIconRes = R.drawable.sym_fill_calendar_clock
    )

    object Settings : BottomNavItem(
        route = "settings",
        titleRes = R.string.nav_settings,
        iconRes = R.drawable.sym_settings,
        selectedIconRes = R.drawable.sym_fill_settings
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Favorites,
    BottomNavItem.Timeline,
    BottomNavItem.Schedule,
    BottomNavItem.Settings
)

/** 底部导航栏内容高度：仅图标/仅文字模式下内容更紧凑，相应降低高度避免留白过多 */
fun bottomNavBarHeight(labelMode: NavigationLabelMode): Dp = when (labelMode) {
    NavigationLabelMode.ICON_ONLY -> 56.dp
    NavigationLabelMode.ICON_AND_TEXT -> 68.dp
    NavigationLabelMode.TEXT_ONLY -> 52.dp
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    visiblePages: List<String> = listOf("home", "favorites", "timeline", "settings"),
    pagerState: PagerState? = null,
    labelMode: NavigationLabelMode = NavigationLabelMode.ICON_AND_TEXT,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Pager 单页宽度（屏幕宽度），用于把导航栏拖拽像素换算为页面数
    val pageWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .pointerInput(pagerState) {
                if (pagerState == null) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (pageWidthPx > 0f) {
                            scope.launch {
                                pagerState.scrollBy(-dragAmount / pageWidthPx)
                            }
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomNavBarHeight(labelMode)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val visibleItems = bottomNavItems.filter { it.route in visiblePages }
                visibleItems.forEach { item ->
                    val selected = currentRoute == item.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigate(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (labelMode != NavigationLabelMode.TEXT_ONLY) {
                            Icon(
                                painter = painterResource(if (selected) item.selectedIconRes else item.iconRes),
                                contentDescription = stringResource(item.titleRes),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (labelMode != NavigationLabelMode.ICON_ONLY) {
                            Text(
                                text = stringResource(item.titleRes),
                                fontSize = if (labelMode == NavigationLabelMode.TEXT_ONLY) 14.sp else 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
