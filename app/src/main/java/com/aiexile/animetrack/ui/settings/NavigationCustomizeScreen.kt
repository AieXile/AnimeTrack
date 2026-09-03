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
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.aiexile.animetrack.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.isCompactWidth
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.StatusBarMode
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.navigation.Routes
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationCustomizeScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val showFavorites by settingsRepository.showFavorites.collectAsState(false)
    val showTimeline by settingsRepository.showTimeline.collectAsState(true)
    val showSchedule by settingsRepository.showSchedule.collectAsState(true)
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.BOTTOM)
    val fabLocation by settingsRepository.fabLocation.collectAsState(FabLocation.BOTTOM_RIGHT)
    val navigationLabelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled.collectAsState(false)
    val capsuleLiquidGlass by settingsRepository.capsuleLiquidGlassEnabled.collectAsState(false)
    // 初始值取同步缓存（已持久化的值），避免首帧渲染默认关闭态、随后跳变为已开启的闪变
    val hideTopBarOnScroll by settingsRepository.hideTopBarOnScrollEnabled
        .collectAsState(settingsRepository.cachedHideTopBarOnScroll())
    val statusBarMode by settingsRepository.statusBarMode
        .collectAsState(settingsRepository.cachedStatusBarMode())

    // 大屏（Medium/Expanded）使用侧边导航栏且无 FAB：导航样式与 FAB 位置设置无效，隐藏对应分组
    val showCompactOnlySettings = isCompactWidth()

    // 搜索定位：高亮目标项并滚动到所在分组（锚点索引随大屏条件分组偏移）
    val highlightKey = rememberSettingsHighlight(Routes.NAVIGATION_CUSTOMIZE)
    val listState = rememberLazyListState()
    val highlightAnchors = buildMap {
        var i = 1
        if (showCompactOnlySettings) {
            put("nav_style", i); put("advanced_blur", i); put("liquid_glass", i); i++
        }
        put("topbar", i); put("hide_topbar", i); put("statusbar", i); i++
        put("label_mode", i); i++
        if (showCompactOnlySettings) {
            put("fab", i); i++
        }
        put("content", i); i++
        put("greeting", i); put("typing_effect", i)
    }
    LaunchedEffect(highlightKey) {
        highlightAnchors[highlightKey]?.let { listState.animateScrollToItem(it) }
    }
    // 搜索定位命中可展开分组时默认展开，避免高亮项收起不可见
    val expandNavStyleGroup = highlightKey == "nav_style" ||
            highlightKey == "advanced_blur" || highlightKey == "liquid_glass"
    val expandFabGroup = highlightKey == "fab"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_custom_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.sym_arrow_back),
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "spacer_top") { Spacer(modifier = Modifier.height(4.dp)) }

            if (showCompactOnlySettings) {
                item(key = "nav_style_group") {
                    ExpandableSettingsGroup(
                        title = stringResource(R.string.nav_custom_style_title),
                        expanded = expandNavStyleGroup
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NavigationStyleCard(
                                style = NavigationStyle.BOTTOM,
                                isSelected = navigationStyle == NavigationStyle.BOTTOM,
                                onClick = { scope.launch { settingsRepository.setNavigationStyle(NavigationStyle.BOTTOM) } }
                            )
                            NavigationStyleCard(
                                style = NavigationStyle.CAPSULE,
                                isSelected = navigationStyle == NavigationStyle.CAPSULE,
                                onClick = { scope.launch { settingsRepository.setNavigationStyle(NavigationStyle.CAPSULE) } }
                            )
                            // 选中胶囊时条件出现：动画展开/收起，避免高度瞬间跳变造成的闪动
                            androidx.compose.animation.AnimatedVisibility(
                                visible = navigationStyle == NavigationStyle.CAPSULE,
                                enter = androidx.compose.animation.expandVertically(
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                ) + androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ),
                                exit = androidx.compose.animation.shrinkVertically(
                                    animationSpec = androidx.compose.animation.core.tween(200)
                                ) + androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(200)
                                )
                            ) {
                                // AnimatedVisibility 的多个直接子项会叠加在同一位置，需用 Column 包裹
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SwitchItem(
                                        title = stringResource(R.string.nav_custom_advanced_blur),
                                        description = stringResource(R.string.nav_custom_advanced_blur_desc),
                                        checked = capsuleAdvancedBlur,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                settingsRepository.setCapsuleAdvancedBlurEnabled(enabled)
                                                // 与液态玻璃互斥：开启高级模糊时关闭液态玻璃
                                                if (enabled) settingsRepository.setCapsuleLiquidGlassEnabled(false)
                                            }
                                        },
                                        itemKey = "advanced_blur",
                                        highlightKey = highlightKey
                                    )
                                    // 液态玻璃：开启后悬浮胶囊使用折射玻璃渲染，与高级模糊互斥
                                    SwitchItem(
                                        title = stringResource(R.string.nav_custom_liquid_glass),
                                        description = stringResource(R.string.nav_custom_liquid_glass_desc),
                                        checked = capsuleLiquidGlass,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                settingsRepository.setCapsuleLiquidGlassEnabled(enabled)
                                                // 与高级模糊互斥：开启液态玻璃时关闭高级模糊
                                                if (enabled) settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                                            }
                                        },
                                        itemKey = "liquid_glass",
                                        highlightKey = highlightKey
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 顶部栏行为：手机与平板均可用
            item(key = "topbar_group") {
                SettingsGroup(
                    title = stringResource(R.string.nav_custom_topbar_title),
                    subtitle = stringResource(R.string.nav_custom_topbar_subtitle),
                    modifier = rememberHighlightModifier("topbar", highlightKey)
                ) {
                    SwitchItem(
                        title = stringResource(R.string.nav_custom_hide_topbar_on_scroll),
                        description = stringResource(R.string.nav_custom_hide_topbar_on_scroll_desc),
                        checked = hideTopBarOnScroll,
                        onCheckedChange = { scope.launch { settingsRepository.setHideTopBarOnScrollEnabled(it) } },
                        itemKey = "hide_topbar",
                        highlightKey = highlightKey
                    )
                    // 状态栏显示方式：仅「下滑隐藏顶栏」开启时可选，动画展开/收起避免高度跳变闪动
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hideTopBarOnScroll,
                        enter = androidx.compose.animation.expandVertically(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ),
                        exit = androidx.compose.animation.shrinkVertically(
                            animationSpec = androidx.compose.animation.core.tween(200)
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(200)
                        )
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.nav_custom_statusbar_mode),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                StatusBarMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = statusBarMode == mode,
                                        onClick = { scope.launch { settingsRepository.setStatusBarMode(mode) } },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = StatusBarMode.entries.size
                                        ),
                                        icon = {},
                                        label = {
                                            Text(
                                                text = stringResource(mode.labelRes),
                                                maxLines = 1,
                                                fontSize = 13.sp
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "label_mode_group") {
                SettingsGroup(
                    title = stringResource(R.string.nav_custom_label_mode_title),
                    subtitle = stringResource(R.string.nav_custom_label_mode_subtitle),
                    modifier = rememberHighlightModifier("label_mode", highlightKey)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        NavigationLabelMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = navigationLabelMode == mode,
                                onClick = { scope.launch { settingsRepository.setNavigationLabelMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = NavigationLabelMode.entries.size
                                ),
                                icon = {},
                                label = {
                                    Text(
                                        text = stringResource(mode.labelRes),
                                        maxLines = 1,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 大屏无 FAB（添加入口在顶栏、回到顶部在侧边导航栏底部），FAB 位置设置无效
            if (showCompactOnlySettings) {
                item(key = "fab_group") {
                    ExpandableSettingsGroup(
                        title = stringResource(R.string.nav_custom_fab_title),
                        expanded = expandFabGroup
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FabLocationCard(
                                location = FabLocation.BOTTOM_RIGHT,
                                isSelected = fabLocation == FabLocation.BOTTOM_RIGHT,
                                onClick = { scope.launch { settingsRepository.setFabLocation(FabLocation.BOTTOM_RIGHT) } }
                            )
                            FabLocationCard(
                                location = FabLocation.TOP_BAR,
                                isSelected = fabLocation == FabLocation.TOP_BAR,
                                onClick = { scope.launch { settingsRepository.setFabLocation(FabLocation.TOP_BAR) } }
                            )
                        }
                    }
                }
            }

            item(key = "content_group") {
                SettingsGroup(
                    title = stringResource(R.string.nav_custom_content_title),
                    subtitle = stringResource(R.string.nav_custom_content_subtitle),
                    modifier = rememberHighlightModifier("content", highlightKey)
                ) {
                    Column {
                        SwitchItem(
                            title = stringResource(R.string.nav_custom_show_favorites),
                            description = stringResource(R.string.nav_custom_show_favorites_desc),
                            checked = showFavorites,
                            onCheckedChange = { scope.launch { settingsRepository.setShowFavorites(it) } }
                        )
                        SwitchItem(
                            title = stringResource(R.string.nav_custom_show_timeline),
                            description = stringResource(R.string.nav_custom_show_timeline_desc),
                            checked = showTimeline,
                            onCheckedChange = { scope.launch { settingsRepository.setShowTimeline(it) } }
                        )
                        SwitchItem(
                            title = stringResource(R.string.nav_custom_show_schedule),
                            description = stringResource(R.string.nav_custom_show_schedule_desc),
                            checked = showSchedule,
                            onCheckedChange = { scope.launch { settingsRepository.setShowSchedule(it) } }
                        )
                    }
                }
            }

            item(key = "greeting_group") {
                SettingsGroup(
                    title = stringResource(R.string.nav_custom_greeting_title),
                    subtitle = stringResource(R.string.nav_custom_greeting_subtitle),
                    modifier = rememberHighlightModifier("greeting", highlightKey)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val greetingTypingEffect by settingsRepository.greetingTypingEffect.collectAsState(true)
                        SwitchItem(
                            title = stringResource(R.string.nav_custom_typing_effect),
                            description = stringResource(R.string.nav_custom_typing_effect_desc),
                            checked = greetingTypingEffect,
                            onCheckedChange = { scope.launch { settingsRepository.setGreetingTypingEffect(it) } },
                            itemKey = "typing_effect",
                            highlightKey = highlightKey
                        )
                        CustomGreetingField(
                            customGreeting = settingsRepository.customGreeting.collectAsState("").value,
                            onGreetingChange = { scope.launch { settingsRepository.setCustomGreeting(it) } }
                        )
                    }
                }
            }

            item(key = "spacer_bottom") { Spacer(modifier = Modifier.height(16.dp)) }
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
            .clip(SquircleShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = borderWeight,
                color = borderColor,
                shape = SquircleShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (style) {
                    NavigationStyle.BOTTOM -> stringResource(R.string.nav_custom_style_bottom_desc)
                    NavigationStyle.CAPSULE -> stringResource(R.string.nav_custom_style_capsule_desc)
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (style == NavigationStyle.BOTTOM) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(28.dp)
                            .clip(SquircleShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = SquircleShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(28.dp)
                            .clip(SquircleShape(100.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = SquircleShape(100.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
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
            .clip(SquircleShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = borderWeight,
                color = borderColor,
                shape = SquircleShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (location) {
                    FabLocation.BOTTOM_RIGHT -> stringResource(R.string.nav_custom_fab_bottom_right_desc)
                    FabLocation.TOP_BAR -> stringResource(R.string.nav_custom_fab_top_bar_desc)
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(48.dp)
                    .clip(SquircleShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = SquircleShape(8.dp)
                    )
            ) {
                if (location == FabLocation.BOTTOM_RIGHT) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 12.dp)
                            .size(16.dp)
                            .clip(SquircleShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.sym_add),
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                    Icon(
                        painter = painterResource(R.drawable.sym_add),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 1.dp)
                            .size(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
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

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    itemKey: String? = null,
    highlightKey: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rememberHighlightModifier(itemKey, highlightKey))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
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

    Column {
        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onGreetingChange(newValue)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.nav_custom_greeting_placeholder),
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
            shape = SquircleShape(12.dp),
            isError = isOverLimit
        )
        if (isOverLimit) {
            Text(
                text = stringResource(R.string.nav_custom_greeting_too_long),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}