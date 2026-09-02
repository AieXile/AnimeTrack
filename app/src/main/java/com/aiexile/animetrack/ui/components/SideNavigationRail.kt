package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.data.NavigationLabelMode

/**
 * 大屏侧边导航栏（标准 M3 NavigationRail 样式）。
 *
 * Medium/Expanded 宽度设备上替代底部导航栏/胶囊导航栏，固定于屏幕左侧，
 * 与底部导航共用 BottomNavItem 定义与 onNavigate 跳转逻辑；
 * Compact（手机）宽度仍走原有底部导航/胶囊导航路径，不受本组件影响。
 *
 * @param bottomContent 渲染在导航项下方的底部槽位（如回到顶部按钮），导航项不足一屏时贴底显示
 */
@Composable
fun SideNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    visiblePages: List<String>,
    labelMode: NavigationLabelMode,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable () -> Unit)? = null
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        // 顶部弹性占位：导航项在垂直方向居中
        Spacer(modifier = Modifier.weight(1f))
        val visibleItems = bottomNavItems.filter { it.route in visiblePages }
        visibleItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    if (labelMode != NavigationLabelMode.TEXT_ONLY) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(item.titleRes),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = if (labelMode != NavigationLabelMode.ICON_ONLY) {
                    {
                        Text(
                            text = stringResource(item.titleRes),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                } else {
                    null
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        // 底部槽位：与导航项之间的弹性空间对称，导航项居中、槽位贴底
        Spacer(modifier = Modifier.weight(1f))
        if (bottomContent != null) {
            bottomContent()
        }
    }
}
