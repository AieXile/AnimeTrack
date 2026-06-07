package com.aiexile.animetrack.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(title = "追踪你的番剧", description = "记录观看进度，管理追番状态，不再忘记看到哪一集"),
    OnboardingPage(title = "多平台同步", description = "一键导入 B站 与 Bangumi 追番列表，自动同步观看进度"),
    OnboardingPage(title = "数据备份与看板", description = "WebDAV 云端备份守护数据，番剧时间表不再错过更新"),
    OnboardingPage(title = "个性定制", description = "主题配色、导航样式、问候语，打造专属追番体验")
)

@Composable
fun OnboardingScreen(
    onStartReveal: (Offset) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 右上角跳过按钮
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (!isLastPage) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pages.size - 1) } },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 核心 Pager 内容
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            OnboardingPageContent(page = page)
        }

        // 底部高保真控制栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PagerIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                onPageClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
            )

            if (isLastPage) {
                Button(
                    onClick = { onStartReveal(buttonCenter) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        buttonCenter = Offset(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(text = "立即开启", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                }
            } else {
                FilledTonalButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(text = "下一步", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 微缩高保真宇宙容器
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 0.82f
                    scaleY = 0.82f
                    alpha = 0.95f
                },
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> MiniHomeScreen()
                1 -> MiniSyncScreen()
                2 -> MiniScheduleScreen()
                3 -> MiniCustomizeScreen()
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = pages[page].title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = pages[page].description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp), lineArrow = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PagerIndicator(pageCount: Int, currentPage: Int, onPageClick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, animationSpec = tween(300), label = "")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    .clickable { onPageClick(index) }
            )
        }
    }
}

// ==========================================
// 📱 PAGE 1: 真实复刻卡片墙 + 悬浮胶囊栏
// ==========================================
@Composable
private fun MiniHomeScreen() {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // 顶部状态栏及标题行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF64B5F6), Color(0xFF1565C0)))))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // “今日有 1 部番剧更新” 提示条占位
                Box(modifier = Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.08f)))
                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 番剧高保真网格墙
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(4) { index ->
                        MiniAnimeGridCard(index)
                    }
                }
            }

            // ✨ 底部完美高保真复刻的悬浮圆形胶囊导航栏
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    modifier = Modifier.width(180.dp).height(44.dp).shadow(4.dp, RoundedCornerShape(24.dp))
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        // 首页激活状态灰底胶囊
                        Box(modifier = Modifier.size(54.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(onSurface.copy(alpha = 0.12.toFloat())), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.2f)))
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.2f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAnimeGridCard(index: Int) {
    val colors = listOf(
        listOf(Color(0xFFFFD180), Color(0xFFFF9100)),
        listOf(Color(0xFFA7FFEB), Color(0xFF00BFA5)),
        listOf(Color(0xFFE1BEE7), Color(0xFFAB47BC)),
        listOf(Color(0xFFC8E6C9), Color(0xFF4CAF50))
    )[index]

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(colors))) {
                // 卡片右上角“计划观看”小标签
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp, 14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.65f)))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(24.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                Box(modifier = Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFB300).copy(alpha = 0.2f)))
            }
        }
    }
}

// ==========================================
// 🔒 PAGE 2: 完美复刻带弹窗的账户绑定页
// ==========================================
@Composable
private fun MiniSyncScreen() {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        // 背景层：虚化的主界面墙
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).graphicsLayer { alpha = 0.25f }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray))
                Box(modifier = Modifier.size(64.dp, 24.dp).background(Color.Gray))
            }
            Spacer(modifier = Modifier.height(24.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(12.dp)).background(Color.LightGray))
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(12.dp)).background(Color.LightGray))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ✨ 居中悬浮的高保真纯白对话框（图 2 核心）
        Card(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.88f).shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 标志性的红黑小头像
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEF5350)))
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 用户名条
                Box(modifier = Modifier.width(70.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.8f)))
                Spacer(modifier = Modifier.height(20.dp))

                // Bilibili 绑定条
                MiniAccountRow(iconBg = Color(0xFFFB7299), isGreenDot = true)
                Spacer(modifier = Modifier.height(10.dp))
                // Bangumi 绑定条
                MiniAccountRow(iconBg = Color(0xFFF09199), isGreenDot = true)

                Spacer(modifier = Modifier.height(16.dp))
                // 自定义头像按钮轮廓
                Surface(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    color = Color.Transparent
                ) {}
            }
        }
    }
}

@Composable
private fun MiniAccountRow(iconBg: Color, isGreenDot: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp).clip(CircleShape).background(iconBg), tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.width(45.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f)))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.width(35.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = 0.4f)))
            }
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isGreenDot) Color(0xFF4CAF50) else Color.LightGray))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
        }
    }
}

// ==========================================
// 📅 PAGE 3: 完美复刻追番看板更新一览
// ==========================================
@Composable
private fun MiniScheduleScreen() {
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 顶部大标题：追番看板
            Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
            
            Spacer(modifier = Modifier.height(20.dp))

            // 完美还原星期横向选择栏 (一 二 三 四 五 六 日)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val onSurface = MaterialTheme.colorScheme.onSurface
                listOf(false, false, false, true, false, false, false).forEach { isSelected ->
                    if (isSelected) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(10.dp, 2.dp).background(Color.White))
                        }
                    } else {
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(10.dp, 2.dp).background(onSurface.copy(alpha = 0.2f)))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 下方两张巨型高清海报排布
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFF57F17))))
                )
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFF0288D1))))
                )
            }
        }
    }
}

// ==========================================
// 🎨 PAGE 4: 个性化定制（色彩调色盘与设置）
// ==========================================
@Composable
private fun MiniCustomizeScreen() {
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            // 色彩调色盘预览
            Box(modifier = Modifier.width(60.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val themeColors = listOf(Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF9C27B0))
                themeColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (index == 0) Modifier.shadow(2.dp, CircleShape).background(Color.Transparent)
                                else Modifier
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 导航样式选择模块
            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 选中的底部导航模拟
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.padding(bottom = 6.dp).width(50.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                }
                // 未选中的胶囊导航模拟
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(44.dp).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 专属问候语组件复刻
            Box(modifier = Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "👋", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(80.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)))
                }
            }
        }
    }
}