package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.SquircleShape
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
    )
)

@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    onStartReveal: (Offset) -> Unit
) {
    val pages = onboardingPages()
    val gateState = rememberOnboardingGateState()
    val pagerState = rememberPagerState(pageCount = { OnboardingPages.COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == OnboardingPages.READY
    // 当前步骤未完成时「下一步」为禁用态；完成判定由 OnboardingGateState 统一提供
    val canContinue = gateState.isStepComplete(pagerState.currentPage)
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        // 大屏（平板/横屏）限制内容最大宽度并居中，避免预览与控件被拉伸过宽
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 480.dp)
                .statusBarsPadding()
        ) {
            // 固定高度占位，跳过按钮在最后一页消失时布局不跳动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // 跳过仅限前 3 页功能介绍，直达主题步骤；进入自定义环节后需逐项完成
                if (pagerState.currentPage < OnboardingPages.INTRO_COUNT) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(OnboardingPages.THEME)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 主 Pager 禁用手势：既不与页内预览的滑动冲突，也避免滑动绕过未完成的步骤
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    in pages.indices -> OnboardingIntroPageContent(page = page, pages = pages)
                    OnboardingPages.THEME -> OnboardingThemePage(
                        settingsRepository = settingsRepository,
                        gateState = gateState
                    )
                    OnboardingPages.ICON_EFFECT -> OnboardingIconEffectPage(
                        settingsRepository = settingsRepository,
                        gateState = gateState
                    )
                    OnboardingPages.NAVIGATION -> OnboardingNavPage(
                        settingsRepository = settingsRepository,
                        gateState = gateState
                    )
                    else -> OnboardingReadyPage(settingsRepository = settingsRepository)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PagerIndicator(
                    pageCount = OnboardingPages.COUNT,
                    currentPage = pagerState.currentPage,
                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                    maxReachedPage = gateState.maxReachedPage,
                    onPageClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
                )

                if (isLastPage) {
                    Button(
                        onClick = { onStartReveal(buttonCenter) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            buttonCenter = Offset(
                                bounds.left + bounds.width / 2,
                                bounds.top + bounds.height / 2
                            )
                        },
                        shape = SquircleShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_start),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        enabled = canContinue,
                        shape = SquircleShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_next),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingIntroPageContent(page: Int, pages: List<OnboardingPage>) {
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
            }
        }

        OnboardingPageHeader(
            title = pages[page].title,
            description = pages[page].description,
            center = true
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    maxReachedPage: Int,
    onPageClick: (Int) -> Unit
) {
    // 计算连续的页面位置，用于平滑插值
    val fractionalPosition = currentPage + currentPageOffsetFraction

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            // 超出已到达范围的页不可点击，视觉上弱化，避免越过未完成步骤
            val locked = index > maxReachedPage
            // 计算该点与当前位置的距离，距离越近宽度越大
            val distance = kotlin.math.abs(fractionalPosition - index)
            val width = (24f + (8f - 24f) * distance.coerceIn(0f, 1f)).dp
            val color = when {
                locked -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                distance < 0.5f -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(enabled = !locked) { onPageClick(index) }
            )
        }
    }
}
