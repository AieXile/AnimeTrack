package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.websource.WebSourceRuleCodec
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 数据源管理：粘贴 Kazumi 规则 JSON 或从链接导入，并维护已导入规则。
 *
 * App 不内置任何站点规则，导入内容由用户自行负责。
 */
@Composable
fun WebSourceManageDialog(
    rules: List<WebSourceRule>,
    importState: WebSourceImportState,
    onImportText: (String) -> Unit,
    onImportUrl: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pasted by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val importing = importState is WebSourceImportState.Loading

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.CLOSE),
                            contentDescription = stringResource(R.string.common_close)
                        )
                    }
                    Text(
                        text = stringResource(R.string.web_source_manage_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.web_source_manage_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.web_source_compatibility_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    AppOutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text(stringResource(R.string.web_source_import_json_label)) },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onImportText(pasted) },
                        enabled = pasted.isNotBlank() && !importing
                    ) {
                        Text(stringResource(R.string.web_source_import_text))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    AppOutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.web_source_import_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onImportUrl(url) },
                        enabled = url.isNotBlank() && !importing
                    ) {
                        Text(stringResource(R.string.web_source_import_url))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ImportStatus(importState)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        text = stringResource(R.string.web_source_rules_count, rules.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    if (rules.isEmpty()) {
                        Text(
                            text = stringResource(R.string.web_source_no_rules_yet),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    rules.forEach { rule ->
                        RuleRow(rule = rule, onDelete = { onDelete(rule.name) })
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ImportStatus(state: WebSourceImportState) {
    when (state) {
        WebSourceImportState.Idle -> Unit
        WebSourceImportState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.web_source_import_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is WebSourceImportState.Success -> Text(
            text = if (state.skipped > 0) {
                stringResource(
                    R.string.web_source_import_success_skipped,
                    state.imported,
                    state.skipped
                )
            } else {
                stringResource(R.string.web_source_import_success, state.imported)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        is WebSourceImportState.Failure -> Text(
            text = stringResource(
                if (state.issues.isEmpty()) R.string.web_source_import_failed
                else R.string.web_source_import_no_supported_rules
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    val issues = when (state) {
        is WebSourceImportState.Success -> state.issues
        is WebSourceImportState.Failure -> state.issues
        else -> emptyList()
    }
    issues.forEach { issue ->
        val reason = when (issue.reason) {
            WebSourceRuleCodec.IssueReason.INCOMPLETE -> R.string.web_source_issue_incomplete
            WebSourceRuleCodec.IssueReason.DUPLICATE -> R.string.web_source_issue_duplicate
            WebSourceRuleCodec.IssueReason.POST -> R.string.web_source_issue_post
            WebSourceRuleCodec.IssueReason.WEB_PLAYER_ONLY -> R.string.web_source_issue_web_player
            WebSourceRuleCodec.IssueReason.LEGACY_PARSER -> R.string.web_source_issue_legacy_parser
            WebSourceRuleCodec.IssueReason.NON_WEBVIEW_PARSER -> R.string.web_source_issue_non_webview
            WebSourceRuleCodec.IssueReason.ANTI_CRAWLER -> R.string.web_source_issue_anti_crawler
            WebSourceRuleCodec.IssueReason.DEPRECATED -> R.string.web_source_issue_deprecated
            WebSourceRuleCodec.IssueReason.INVALID_OPTION -> R.string.web_source_issue_invalid_option
            WebSourceRuleCodec.IssueReason.INVALID_HEADERS -> R.string.web_source_issue_invalid_headers
            WebSourceRuleCodec.IssueReason.AD_BLOCKING_IGNORED -> R.string.web_source_issue_ad_blocking
        }
        Text(
            text = stringResource(
                if (issue.reason.rejectsRule) R.string.web_source_import_issue
                else R.string.web_source_import_warning,
                issue.ruleName ?: stringResource(R.string.web_source_unnamed_rule),
                stringResource(reason)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.reason.rejectsRule) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RuleRow(rule: WebSourceRule, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rule.baseUrl.isNotBlank()) {
                Text(
                    text = rule.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.DELETE),
                contentDescription = stringResource(R.string.common_delete)
            )
        }
    }
}
