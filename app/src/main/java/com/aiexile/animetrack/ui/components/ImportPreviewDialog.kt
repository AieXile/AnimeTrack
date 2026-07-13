package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ImportResult

@Composable
fun ImportPreviewDialog(
    importResult: ImportResult,
    duplicateCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.import_preview_title),
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.import_preview_detected),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (importResult.watchingCount > 0) {
                    StatRow(
                        label = stringResource(R.string.import_preview_watching),
                        count = importResult.watchingCount
                    )
                }
                
                if (importResult.completedCount > 0) {
                    StatRow(
                        label = stringResource(R.string.status_watched),
                        count = importResult.completedCount
                    )
                }
                
                if (importResult.plannedCount > 0) {
                    StatRow(
                        label = stringResource(R.string.import_preview_planned),
                        count = importResult.plannedCount
                    )
                }
                
                if (importResult.droppedCount > 0) {
                    StatRow(
                        label = stringResource(R.string.import_preview_dropped),
                        count = importResult.droppedCount
                    )
                }
                
                if (duplicateCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.import_preview_duplicate, duplicateCount),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val totalToImport = importResult.animes.size - duplicateCount
                Text(
                    text = stringResource(R.string.import_preview_total, totalToImport),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.import_preview_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun StatRow(
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.import_preview_count, count),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
