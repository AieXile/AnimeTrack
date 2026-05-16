package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationCustomizeScreen(
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val showFavorites by themeViewModel.showFavorites.collectAsState()
    val showTimeline by themeViewModel.showTimeline.collectAsState()
    val showSchedule by themeViewModel.showSchedule.collectAsState()
    val navigationStyle by themeViewModel.navigationStyle.collectAsState()
    val fabLocation by themeViewModel.fabLocation.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "定制导航栏",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Text(
                    text = "导航栏样式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                NavigationStyleCard(
                    style = NavigationStyle.BOTTOM,
                    isSelected = navigationStyle == NavigationStyle.BOTTOM,
                    onClick = { themeViewModel.setNavigationStyle(NavigationStyle.BOTTOM) }
                )
            }

            item {
                NavigationStyleCard(
                    style = NavigationStyle.CAPSULE,
                    isSelected = navigationStyle == NavigationStyle.CAPSULE,
                    onClick = { themeViewModel.setNavigationStyle(NavigationStyle.CAPSULE) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "导航栏内容",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                NavigationItem(
                    title = "显示收藏",
                    description = "在导航栏显示收藏入口",
                    checked = showFavorites,
                    onCheckedChange = { themeViewModel.setShowFavorites(it) }
                )
            }

            item {
                NavigationItem(
                    title = "显示时间线",
                    description = "在导航栏显示时间线入口",
                    checked = showTimeline,
                    onCheckedChange = { themeViewModel.setShowTimeline(it) }
                )
            }

            item {
                NavigationItem(
                    title = "显示追番看板",
                    description = "在导航栏显示追番看板入口",
                    checked = showSchedule,
                    onCheckedChange = { themeViewModel.setShowSchedule(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "添加番剧按钮位置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                FabLocationCard(
                    location = FabLocation.BOTTOM_RIGHT,
                    isSelected = fabLocation == FabLocation.BOTTOM_RIGHT,
                    onClick = { themeViewModel.setFabLocation(FabLocation.BOTTOM_RIGHT) }
                )
            }

            item {
                FabLocationCard(
                    location = FabLocation.TOP_BAR,
                    isSelected = fabLocation == FabLocation.TOP_BAR,
                    onClick = { themeViewModel.setFabLocation(FabLocation.TOP_BAR) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "自定义欢迎语",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            item {
                CustomGreetingField(
                    customGreeting = themeViewModel.customGreeting.collectAsState().value,
                    onGreetingChange = { themeViewModel.setCustomGreeting(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NavigationStyleCard(
    style: NavigationStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWeight = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = borderWeight,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = style.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (style) {
                    NavigationStyle.BOTTOM -> "经典 Material Design 底部导航栏，贴底显示"
                    NavigationStyle.CAPSULE -> "悬浮胶囊导航栏，圆角浮于内容之上"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (style == NavigationStyle.BOTTOM) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(100.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private const val GREETING_MAX_WEIGHT = 24

private fun calculateGreetingWeight(text: String): Int {
    var weight = 0
    for (char in text) {
        weight += if (char.code > 0x2E7F) 2 else 1
    }
    return weight
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomGreetingField(
    customGreeting: String,
    onGreetingChange: (String) -> Unit
) {
    var textFieldValue by remember(customGreeting) { mutableStateOf(customGreeting) }
    val currentWeight = calculateGreetingWeight(textFieldValue)
    val isOverLimit = currentWeight > GREETING_MAX_WEIGHT

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "设置首页标题栏显示的欢迎语",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onGreetingChange(newValue)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "留空则显示随机 Hi, / Hey, / Hello,",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            isError = isOverLimit
        )
        if (isOverLimit) {
            Text(
                text = "文案过长可能会被截断",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                text = title,
                fontSize = 16.sp
            )
        },
        supportingContent = {
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun FabLocationCard(
    location: FabLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWeight = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = borderWeight,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = location.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (location) {
                    FabLocation.BOTTOM_RIGHT -> "悬浮在页面右下角，经典 Material 风格"
                    FabLocation.TOP_BAR -> "嵌入顶部标题栏右侧，更极简紧凑"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
