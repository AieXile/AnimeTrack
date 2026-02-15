package com.animetrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animetrack.model.AnimeStatus
import com.animetrack.ui.theme.Primary
import com.animetrack.ui.theme.StarFilled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AddAnimeFormState(
    val title: String = "",
    val totalEpisodes: Int = 12,
    val watchedEpisodes: Int = 0,
    val status: AnimeStatus = AnimeStatus.WATCHING,
    val rating: Float? = null,
    val notes: String = "",
    val startDate: Long? = null,
    val finishDate: Long? = null,
    val coverUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAnimeForm(
    formState: AddAnimeFormState,
    onFormStateChange: (AddAnimeFormState) -> Unit,
    watchedEpisodesError: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "添加新番剧",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = formState.title,
            onValueChange = { onFormStateChange(formState.copy(title = it)) },
            label = { Text("番剧名称") },
            placeholder = { Text("输入番剧名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary
            )
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumberInputField(
                label = "总集数",
                value = formState.totalEpisodes,
                onValueChange = { onFormStateChange(formState.copy(totalEpisodes = it)) },
                modifier = Modifier.weight(1f),
                minValue = 1
            )
            
            NumberInputField(
                label = "已看集数",
                value = formState.watchedEpisodes,
                onValueChange = { onFormStateChange(formState.copy(watchedEpisodes = it)) },
                modifier = Modifier.weight(1f),
                minValue = 0,
                maxValue = formState.totalEpisodes,
                error = watchedEpisodesError
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        StatusDropdown(
            selectedStatus = formState.status,
            onStatusChange = { newStatus ->
                val newState = if (newStatus == AnimeStatus.COMPLETED && formState.finishDate == null) {
                    formState.copy(status = newStatus, finishDate = System.currentTimeMillis())
                } else {
                    formState.copy(status = newStatus)
                }
                onFormStateChange(newState)
            }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        DateSelectors(
            status = formState.status,
            startDate = formState.startDate,
            finishDate = formState.finishDate,
            onStartDateChange = { onFormStateChange(formState.copy(startDate = it)) },
            onFinishDateChange = { onFormStateChange(formState.copy(finishDate = it)) }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        RatingSelector(
            rating = formState.rating,
            onRatingChange = { onFormStateChange(formState.copy(rating = it)) }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        OutlinedTextField(
            value = formState.notes,
            onValueChange = { onFormStateChange(formState.copy(notes = it)) },
            label = { Text("备注") },
            placeholder = { Text("添加备注...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary
            )
        )
    }
}

@Composable
private fun NumberInputField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
    error: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (value > minValue) onValueChange(value - 1) },
                enabled = value > minValue
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "减少"
                )
            }
            
            Text(
                text = value.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            IconButton(
                onClick = { if (value < maxValue) onValueChange(value + 1) },
                enabled = value < maxValue
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "增加"
                )
            }
        }
        
        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(
    selectedStatus: AnimeStatus,
    onStatusChange: (AnimeStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "观看状态",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedStatus.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    focusedLabelColor = Primary
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                AnimeStatus.entries.forEach { status ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(status.displayName) },
                        onClick = {
                            onStatusChange(status)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingSelector(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "评分",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = if (rating != null) "${rating} 分" else "未评分",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (rating != null) Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(5) { index ->
                    val starValue = index + 1
                    val isSelected = rating != null && rating >= starValue
                    
                    IconButton(
                        onClick = {
                            val newRating = if (rating == starValue.toFloat()) null else starValue.toFloat()
                            onRatingChange(newRating)
                        },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "$starValue 星",
                            tint = if (isSelected) StarFilled else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp).height(28.dp)
                        )
                    }
                }
            }
            
            if (rating != null) {
                TextButton(
                    onClick = { onRatingChange(null) }
                ) {
                    Text(
                        text = "清除",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectors(
    status: AnimeStatus,
    startDate: Long?,
    finishDate: Long?,
    onStartDateChange: (Long?) -> Unit,
    onFinishDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showFinishDatePicker by remember { mutableStateOf(false) }
    
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        when (status) {
            AnimeStatus.WATCHING -> {
                Text(
                    text = "正在观看中...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            AnimeStatus.COMPLETED -> {
                DatePickerField(
                    label = "完成日期",
                    date = finishDate,
                    dateFormat = dateFormat,
                    onClick = { showFinishDatePicker = true },
                    onClear = { onFinishDateChange(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimeStatus.DROPPED -> {
                DatePickerField(
                    label = "弃番日期",
                    date = finishDate,
                    dateFormat = dateFormat,
                    onClick = { showFinishDatePicker = true },
                    onClear = { onFinishDateChange(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimeStatus.PLANNED -> {
                Text(
                    text = "计划观看",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
    
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { showStartDatePicker = false }
                ) {
                    Text("确定")
                }
            }
        ) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = startDate ?: System.currentTimeMillis()
            )
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
            LaunchedEffect(datePickerState.selectedDateMillis) {
                onStartDateChange(datePickerState.selectedDateMillis)
            }
        }
    }
    
    if (showFinishDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFinishDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { showFinishDatePicker = false }
                ) {
                    Text("确定")
                }
            }
        ) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = finishDate ?: System.currentTimeMillis()
            )
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
            LaunchedEffect(datePickerState.selectedDateMillis) {
                onFinishDateChange(datePickerState.selectedDateMillis)
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    date: Long?,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = if (date != null) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (date != null) dateFormat.format(Date(date)) else "选择日期",
                    fontSize = 16.sp,
                    color = if (date != null) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (date != null) {
                TextButton(onClick = onClear) {
                    Text(
                        text = "清除",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
