package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
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
            put("nav_style", i); i++
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
    val expandNavStyleGroup = highlightKey == "nav_style"
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
                            painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NavigationStyleCard(
                                modifier = Modifier.weight(1f),
                                style = NavigationStyle.BOTTOM,
                                isSelected = navigationStyle == NavigationStyle.BOTTOM,
                                onClick = { scope.launch { settingsRepository.setNavigationStyle(NavigationStyle.BOTTOM) } }
                            )
                            NavigationStyleCard(
                                modifier = Modifier.weight(1f),
                                style = NavigationStyle.CAPSULE,
                                isSelected = navigationStyle == NavigationStyle.CAPSULE,
                                onClick = { scope.launch { settingsRepository.setNavigationStyle(NavigationStyle.CAPSULE) } }
                            )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FabLocationCard(
                                modifier = Modifier.weight(1f),
                                location = FabLocation.BOTTOM_RIGHT,
                                isSelected = fabLocation == FabLocation.BOTTOM_RIGHT,
                                onClick = { scope.launch { settingsRepository.setFabLocation(FabLocation.BOTTOM_RIGHT) } }
                            )
                            FabLocationCard(
                                modifier = Modifier.weight(1f),
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

/** 导航样式卡片：标签在上、预览居中、描述在下，选中态仅以外框标识（并排布局用竖版卡片） */
@Composable
private fun NavigationStyleCard(
    style: NavigationStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = style.displayName,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(SquircleShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                )
                .clickable(onClick = onClick)
        ) {
            // 页面内容示意线条
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(5.dp)
                        .clip(SquircleShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(5.dp)
                        .clip(SquircleShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                )
            }
            if (style == NavigationStyle.BOTTOM) {
                // 贴底导航条：贴满卡片底边
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
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
                // 悬浮胶囊：左右与底部留边，全圆角浮于内容之上
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(SquircleShape(100.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = SquircleShape(100.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
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
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (style) {
                NavigationStyle.BOTTOM -> stringResource(R.string.nav_custom_style_bottom_desc)
                NavigationStyle.CAPSULE -> stringResource(R.string.nav_custom_style_capsule_desc)
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** 添加按钮位置卡片：标签在上、预览居中、描述在下，选中态仅以外框标识（并排布局用竖版卡片） */
@Composable
private fun FabLocationCard(
    location: FabLocation,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = location.displayName,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(SquircleShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                )
                .clickable(onClick = onClick)
        ) {
            // 页面内容示意线条（顶栏模式时避开顶部条）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (location == FabLocation.TOP_BAR) 22.dp else 8.dp,
                        bottom = 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(5.dp)
                        .clip(SquircleShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(5.dp)
                        .clip(SquircleShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                )
            }
            if (location == FabLocation.BOTTOM_RIGHT) {
                // 底部导航条 + 右下角悬浮按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 14.dp)
                        .size(22.dp)
                        .clip(SquircleShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ADD),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                // 顶部标题栏（左侧标题块）+ 顶栏右侧添加按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp)
                            .width(24.dp)
                            .height(4.dp)
                            .clip(SquircleShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 5.dp, top = 1.5.dp)
                        .size(11.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (location) {
                FabLocation.BOTTOM_RIGHT -> stringResource(R.string.nav_custom_fab_bottom_right_desc)
                FabLocation.TOP_BAR -> stringResource(R.string.nav_custom_fab_top_bar_desc)
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(start = 4.dp)
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