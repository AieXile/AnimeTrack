package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 网页播放页的规则数据源入口：选择站点、输入番剧名、发起搜索。
 *
 * 只负责收集输入，网络请求与状态由 [WebSourceViewModel] 持有。
 */
@Composable
fun WebSourceSection(
    rules: List<WebSourceRule>,
    selectedRule: WebSourceRule?,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSelectRule: (WebSourceRule) -> Unit,
    onSearch: () -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.web_source_section_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManage) {
                Text(stringResource(R.string.web_source_manage))
            }
        }

        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.web_source_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedRule?.name ?: stringResource(R.string.web_source_select),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ARROW_DROP_DOWN),
                        contentDescription = null
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    rules.forEach { rule ->
                        DropdownMenuItem(
                            text = { Text(rule.name) },
                            onClick = {
                                onSelectRule(rule)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppOutlinedTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    placeholder = { Text(stringResource(R.string.web_source_keyword_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxHeight(),
                    enabled = keyword.isNotBlank() && selectedRule != null
                ) {
                    Text(stringResource(R.string.common_search))
                }
            }

            Text(
                text = stringResource(R.string.web_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
