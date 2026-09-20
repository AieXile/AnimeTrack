package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.MarkdownText
import com.aiexile.animetrack.ui.components.MarkdownTextStyle
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 隐私政策长文档排版：较默认紧凑样式更大字号（正文 14sp）与更宽的
 * 块间距/列表间距，配合生效日期标签，提升长文档可读性。
 */
private val PrivacyPolicyMarkdownStyle = MarkdownTextStyle(
    h1 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    paragraph = TextStyle(fontSize = 14.sp, lineHeight = 24.sp),
    listItem = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    blockSpacing = 8.dp,
    listItemSpacing = 3.dp,
    headingSpacing = 10.dp
)

/**
 * 隐私政策页面：Markdown 内容按语言存放于 res/raw/privacy_policy.md
 * （raw / raw-zh-rTW / raw-en，资源系统按当前语言自动匹配）。
 * 不放 string 资源的原因：AAPT2 编译 XML string 时会将换行折叠为空格，
 * Markdown 按行解析的语法（标题/列表/空行分段）会全部失效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val content = remember {
        runCatching {
            context.resources.openRawResource(R.raw.privacy_policy)
                .bufferedReader().use { it.readText() }
        }.getOrElse { "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.privacy_policy_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 生效日期标签
            Surface(
                shape = SquircleShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    text = stringResource(R.string.privacy_policy_effective_date),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            MarkdownText(
                markdown = content,
                style = PrivacyPolicyMarkdownStyle,
                modifier = Modifier.fillMaxWidth()
            )

            // 底部留白，长文档滚动到末尾不贴边
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
