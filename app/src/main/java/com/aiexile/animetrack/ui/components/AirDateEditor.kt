package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.util.formatAirDateDisplay
import com.aiexile.animetrack.util.parseAirDateToLocalDate
import androidx.compose.ui.res.painterResource

/**
 * 放送日期编辑器（紧凑行式）：图标 + 当前值/未定 + 清空按钮，点击弹出选择 Dialog。
 * 支持完整日期（yyyy-MM-dd）与仅年月（yyyy-MM）两种模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirDateEditor(
    airDate: String?,
    onAirDateChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val displayText = formatAirDateDisplay(airDate)
        ?: stringResource(R.string.detail_air_date_undetermined)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .clickable { showPicker = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.sym_calendar_month),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = displayText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (airDate != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onAirDateChange(null) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.sym_close),
                    contentDescription = stringResource(R.string.common_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }

    if (showPicker) {
        AirDatePickerDialog(
            initialAirDate = airDate,
            onConfirm = { dateStr ->
                onAirDateChange(dateStr)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * 放送日期选择 Dialog：单 Dialog 内切换「完整日期」（M3 DatePicker）与「仅年月」（年/月输入）。
 * 关闭平台默认窗口宽度限制，DatePicker 铺满弹窗（328~360dp），避免星期表头被挤压。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirDatePickerDialog(
    initialAirDate: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var yearMonthMode by remember {
        mutableStateOf(initialAirDate?.matches(Regex("^\\d{4}-\\d{2}$")) == true)
    }

    val initialLocalDate = parseAirDateToLocalDate(initialAirDate)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialLocalDate
            ?.atStartOfDay(java.time.ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
            ?: System.currentTimeMillis()
    )

    val initialYear = initialLocalDate?.year ?: java.time.Year.now().value
    val initialMonth = initialLocalDate?.monthValue ?: 1
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var yearText by remember { mutableStateOf(initialYear.toString()) }
    var monthText by remember { mutableStateOf(initialMonth.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(min = 328.dp, max = 360.dp),
            shape = SquircleShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.detail_edit_air_date),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AirDateModeChip(
                            text = stringResource(R.string.detail_air_date_mode_full),
                            selected = !yearMonthMode,
                            onClick = { yearMonthMode = false }
                        )
                        AirDateModeChip(
                            text = stringResource(R.string.detail_air_date_mode_year_month),
                            selected = yearMonthMode,
                            onClick = { yearMonthMode = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (yearMonthMode) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { v ->
                                val filtered = v.filter { it.isDigit() }
                                yearText = filtered
                                filtered.toIntOrNull()?.let { if (it in 1900..2100) year = it }
                            },
                            label = { Text(stringResource(R.string.detail_year_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = SquircleShape(12.dp)
                        )
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { v ->
                                val filtered = v.filter { it.isDigit() }
                                monthText = filtered
                                filtered.toIntOrNull()?.let { if (it in 1..12) month = it }
                            },
                            label = { Text(stringResource(R.string.detail_month_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = SquircleShape(12.dp)
                        )
                    }
                } else {
                    // DatePicker 铺满弹窗宽度（其最小宽度为 328dp），
                    // 周围不再加水平内边距，避免周日/周六表头被挤压重叠
                    DatePicker(state = datePickerState, showModeToggle = false)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        val result = if (yearMonthMode) {
                            String.format("%04d-%02d", year, month)
                        } else {
                            datePickerState.selectedDateMillis?.let { millis ->
                                java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString()
                            }
                        }
                        onConfirm(result)
                    }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirDateModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animeColors = LocalAnimeColors.current
    Surface(
        onClick = onClick,
        shape = SquircleShape(8.dp),
        color = if (selected) animeColors.chipSelectedContainer
            else animeColors.chipUnselectedContainer,
        contentColor = if (selected) animeColors.chipSelectedContent
            else animeColors.chipUnselectedContent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp
        )
    }
}
