package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==========================================
// PAGE 1: 卡片墙 + 悬浮胶囊栏
// ==========================================
@Composable
internal fun MiniHomeScreen() {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .shadow(8.dp, SquircleShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(28.dp)),
        shape = SquircleShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.15f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(24.dp), tint = onSurface)
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
                            Icon(Icons.Rounded.Home, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
internal fun MiniSyncScreen() {
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
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f)
                .shadow(16.dp, SquircleShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(24.dp)),
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
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = onSurface.copy(alpha = 0.3f))
        }
    }
}

// ==========================================
// PAGE 3: 追番看板
// ==========================================
@Composable
internal fun MiniScheduleScreen() {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .shadow(8.dp, SquircleShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(28.dp)),
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
