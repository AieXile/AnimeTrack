package com.aiexile.animetrack.ui.onboarding

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String
)

@Composable
private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        title = stringResource(R.string.onboarding_page1_title),
        description = stringResource(R.string.onboarding_page1_description)
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_page2_title),
        description = stringResource(R.string.onboarding_page2_description)
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_page3_title),
        description = stringResource(R.string.onboarding_page3_description)
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_page4_title),
        description = stringResource(R.string.onboarding_page4_description)
    )
)

@Composable
fun OnboardingScreen(
    onStartReveal: (Offset) -> Unit
) {
    val pages = onboardingPages()
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
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (!isLastPage) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pages.size - 1) } },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            OnboardingPageContent(page = page)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PagerIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                onPageClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
            )

            if (isLastPage) {
                Button(
                    onClick = { onStartReveal(buttonCenter) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        buttonCenter = Offset(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
                    },
                    shape = SquircleShape(24.dp)
                ) {
                    Text(text = stringResource(R.string.onboarding_start), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                }
            } else {
                FilledTonalButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    shape = SquircleShape(24.dp)
                ) {
                    Text(text = stringResource(R.string.onboarding_next), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: Int) {
    val pages = onboardingPages()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
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
        Text(text = pages[page].description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp), lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PagerIndicator(pageCount: Int, currentPage: Int, currentPageOffsetFraction: Float, onPageClick: (Int) -> Unit) {
    // 计算连续的页面位置，用于平滑插值
    val fractionalPosition = currentPage + currentPageOffsetFraction

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            // 计算该点与当前位置的距离，距离越近宽度越大
            val distance = kotlin.math.abs(fractionalPosition - index)
            val width = (24f + (8f - 24f) * distance.coerceIn(0f, 1f)).dp
            val color = if (distance < 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onPageClick(index) }
            )
        }
    }
}

// ==========================================
// PAGE 1: 卡片墙 + 悬浮胶囊栏
// ==========================================
@Composable
private fun MiniHomeScreen() {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, SquircleShape(28.dp)),
        shape = SquircleShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.15f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.width(110.dp).height(12.dp).clip(SquircleShape(6.dp)).background(onSurface.copy(alpha = 0.08f)))
                Spacer(modifier = Modifier.height(12.dp))

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

            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                Surface(
                    shape = SquircleShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    modifier = Modifier.width(180.dp).height(44.dp).shadow(4.dp, SquircleShape(24.dp))
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(54.dp, 32.dp).clip(SquircleShape(16.dp)).background(onSurface.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    // 统一灰色调，用不同深浅区分层次
    val coverAlphas = listOf(0.12f, 0.18f, 0.08f, 0.15f)

    Card(
        shape = SquircleShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(SquircleShape(14.dp)).background(onSurface.copy(alpha = coverAlphas[index]))) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp, 14.dp).clip(SquircleShape(4.dp)).background(Color.Black.copy(alpha = 0.4f)))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(10.dp).clip(SquircleShape(5.dp)).background(onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(24.dp).height(8.dp).clip(SquircleShape(4.dp)).background(onSurface.copy(alpha = 0.3f)))
                Box(modifier = Modifier.width(28.dp).height(8.dp).clip(SquircleShape(4.dp)).background(onSurface.copy(alpha = 0.15f)))
            }
        }
    }
}

// ==========================================
// PAGE 2: 账号绑定页
// ==========================================
@Composable
private fun MiniSyncScreen() {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        // 背景层
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).graphicsLayer { alpha = 0.25f }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.15f)))
                Box(modifier = Modifier.size(64.dp, 24.dp).background(onSurface.copy(alpha = 0.1f)))
            }
            Spacer(modifier = Modifier.height(24.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(SquircleShape(12.dp)).background(onSurface.copy(alpha = 0.06f)))
                    Box(modifier = Modifier.weight(1f).aspectRatio(0.8f).clip(SquircleShape(12.dp)).background(onSurface.copy(alpha = 0.06f)))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 居中悬浮对话框
        Card(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.88f).shadow(16.dp, SquircleShape(24.dp)),
            shape = SquircleShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 头像
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.08f)))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.width(70.dp).height(12.dp).clip(SquircleShape(6.dp)).background(onSurface.copy(alpha = 0.6f)))
                Spacer(modifier = Modifier.height(20.dp))

                MiniAccountRow(isConnected = true)
                Spacer(modifier = Modifier.height(10.dp))
                MiniAccountRow(isConnected = true)

                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = SquircleShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = Color.Transparent
                ) {}
            }
        }
    }
}

@Composable
private fun MiniAccountRow(isConnected: Boolean) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = SquircleShape(14.dp),
        color = onSurface.copy(alpha = 0.04f)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.width(45.dp).height(8.dp).clip(SquircleShape(4.dp)).background(onSurface.copy(alpha = 0.5f)))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.width(35.dp).height(6.dp).clip(SquircleShape(3.dp)).background(onSurface.copy(alpha = 0.2f)))
            }
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isConnected) MaterialTheme.colorScheme.primary else onSurface.copy(alpha = 0.2f)))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = onSurface.copy(alpha = 0.3f))
        }
    }
}

// ==========================================
// PAGE 3: 追番看板
// ==========================================
@Composable
private fun MiniScheduleScreen() {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, SquircleShape(28.dp)),
        shape = SquircleShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(modifier = Modifier.width(80.dp).height(16.dp).clip(SquircleShape(4.dp)).background(onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(SquircleShape(3.dp)).background(onSurface.copy(alpha = 0.3f)))

            Spacer(modifier = Modifier.height(20.dp))

            // 星期选择栏
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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

            // 海报排布
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(0.72f).clip(SquircleShape(14.dp))
                        .background(onSurface.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(0.72f).clip(SquircleShape(14.dp))
                        .background(onSurface.copy(alpha = 0.08f))
                )
            }
        }
    }
}

// ==========================================
// PAGE 4: 个性化定制
// ==========================================
@Composable
private fun MiniCustomizeScreen() {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).shadow(8.dp, SquircleShape(28.dp)),
        shape = SquircleShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            // 色彩调色盘预览
            Box(modifier = Modifier.width(60.dp).height(10.dp).clip(SquircleShape(5.dp)).background(onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val circleAlphas = listOf(0.9f, 0.6f, 0.4f, 0.25f, 0.12f)
                circleAlphas.forEachIndexed { index, alpha ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(onSurface.copy(alpha = alpha))
                            .then(
                                if (index == 0) Modifier.shadow(2.dp, CircleShape)
                                else Modifier
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 导航样式选择
            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(SquircleShape(5.dp)).background(onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SquircleShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.padding(bottom = 6.dp).width(50.dp).height(12.dp).clip(SquircleShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SquircleShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(44.dp).height(16.dp).clip(SquircleShape(8.dp)).background(onSurface.copy(alpha = 0.1f)))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 问候语组件
            Box(modifier = Modifier.width(40.dp).height(10.dp).clip(SquircleShape(5.dp)).background(onSurface.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = SquircleShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "👋", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(80.dp).height(10.dp).clip(SquircleShape(5.dp)).background(onSurface.copy(alpha = 0.4f)))
                }
            }
        }
    }
}
