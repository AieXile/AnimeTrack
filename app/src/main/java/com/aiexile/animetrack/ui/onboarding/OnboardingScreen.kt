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
import com.aiexile.animetrack.ui.components.SquircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
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

/** 向导总页数：3 页功能介绍 + 主题页 + 导航页 + 完成页 */
private const val ONBOARDING_PAGE_COUNT = 6

/** 第一个可交互自定义页（主题页）索引：跳过按钮跳过功能介绍直达此处 */
private const val CUSTOMIZE_PAGE_INDEX = 3

@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    onStartReveal: (Offset) -> Unit
) {
    val pages = onboardingPages()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        // 大屏（平板/横屏）限制内容最大宽度并居中，避免预览与控件被拉伸过宽
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 480.dp)
        ) {
            // 固定高度占位，跳过按钮在最后一页消失时布局不跳动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                if (!isLastPage) {
                    // 介绍页跳过直达自定义环节；自定义页跳过直达完成页
                    val skipTarget = if (pagerState.currentPage < CUSTOMIZE_PAGE_INDEX) CUSTOMIZE_PAGE_INDEX else ONBOARDING_PAGE_COUNT - 1
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(skipTarget) } },
                        modifier = Modifier.align(Alignment.CenterEnd)
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
                when (page) {
                    in pages.indices -> OnboardingIntroPageContent(page = page, pages = pages)
                    CUSTOMIZE_PAGE_INDEX -> OnboardingThemePage(settingsRepository = settingsRepository)
                    CUSTOMIZE_PAGE_INDEX + 1 -> OnboardingNavPage(settingsRepository = settingsRepository)
                    else -> OnboardingReadyPage(settingsRepository = settingsRepository)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PagerIndicator(
                    pageCount = ONBOARDING_PAGE_COUNT,
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
