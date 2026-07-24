package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.util.formatDate
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarConfig
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize

data class AddAnimeFormState(
    val title: String = "",
    val totalEpisodes: Int = 12,
    val watchedEpisodes: Int = 0,
    val status: AnimeStatus = AnimeStatus.WATCHING,
    val rating: Float? = null,
    val notes: String = "",
    val startDate: Long? = null,
    val finishDate: Long? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
    val airDate: String? = null,
    val airWeekday: Int? = null,
    val currentEpisodes: Int = 0
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
    ) {
        
        OutlinedTextField(
            value = formState.title,
            onValueChange = { onFormStateChange(formState.copy(title = it)) },
            label = { Text(stringResource(R.string.add_anime_title_label)) },
            placeholder = { Text(stringResource(R.string.add_anime_title_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = SquircleShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumberInputField(
                label = stringResource(R.string.add_anime_total_episodes),
                value = formState.totalEpisodes,
                onValueChange = { onFormStateChange(formState.copy(totalEpisodes = it)) },
                modifier = Modifier.weight(1f),
                minValue = 1
            )
            
            if (formState.status != AnimeStatus.COMPLETED) {
                NumberInputField(
                    label = stringResource(R.string.add_anime_watched_episodes),
                    value = formState.watchedEpisodes,
                    onValueChange = { onFormStateChange(formState.copy(watchedEpisodes = it)) },
                    modifier = Modifier.weight(1f),
                    minValue = 0,
                    maxValue = formState.totalEpisodes,
                    error = watchedEpisodesError
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        StatusDropdown(
            selectedStatus = formState.status,
            onStatusChange = { newStatus ->
                val newState = when (newStatus) {
                    AnimeStatus.COMPLETED -> {
                        formState.copy(
                            status = newStatus,
                            watchedEpisodes = formState.totalEpisodes,
                            finishDate = formState.finishDate ?: System.currentTimeMillis()
                        )
                    }
                    AnimeStatus.WATCHING, AnimeStatus.PLANNED -> {
                        if (formState.status == AnimeStatus.COMPLETED) {
                            formState.copy(
                                status = newStatus,
                                watchedEpisodes = 0,
                                finishDate = null
                            )
                        } else {
                            formState.copy(status = newStatus)
                        }
                    }
                    else -> {
                        formState.copy(status = newStatus)
                    }
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
            label = { Text(stringResource(R.string.add_anime_notes)) },
            placeholder = { Text(stringResource(R.string.add_anime_notes_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
            shape = SquircleShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
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
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue()) }
    val focusReq = remember { FocusRequester() }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { if (value > minValue) onValueChange(value - 1) },
                enabled = value > minValue,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.add_anime_decrease),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isEditing) {
                    BasicTextField(
                        value = editValue,
                        onValueChange = { input ->
                            val filtered = input.copy(text = input.text.filter { it.isDigit() })
                            editValue = filtered
                            val num = filtered.text.toIntOrNull()
                            if (num != null) {
                                val clamped = num.coerceIn(minValue, maxValue)
                                onValueChange(clamped)
                            }
                        },
                        modifier = Modifier
                            .width(48.dp)
                            .focusRequester(focusReq),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )

                    LaunchedEffect(isEditing) {
                        if (isEditing) {
                            editValue = TextFieldValue(
                                text = value.toString(),
                                selection = androidx.compose.ui.text.TextRange(value.toString().length)
                            )
                            focusReq.requestFocus()
                        }
                    }
                } else {
                    Text(
                        text = value.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            isEditing = true
                            editValue = TextFieldValue(
                                text = value.toString(),
                                selection = androidx.compose.ui.text.TextRange(value.toString().length)
                            )
                        }
                    )
                }
            }
            
            IconButton(
                onClick = { if (value < maxValue) onValueChange(value + 1) },
                enabled = value < maxValue,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_anime_increase),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LaunchedEffect(isEditing) {
            if (!isEditing) return@LaunchedEffect
            val currentText = editValue.text
            val currentValue = currentText.toIntOrNull() ?: value
            if (currentValue != value) {
                editValue = TextFieldValue(
                    text = value.toString(),
                    selection = androidx.compose.ui.text.TextRange(value.toString().length)
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
    val animeColors = LocalAnimeColors.current

    val statusColor = when (selectedStatus) {
        AnimeStatus.WATCHING -> animeColors.watching
        AnimeStatus.COMPLETED -> animeColors.finished
        AnimeStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
        AnimeStatus.DROPPED -> animeColors.dropped
    }

    val statusContainerColor = when (selectedStatus) {
        AnimeStatus.WATCHING -> animeColors.watchingContainer
        AnimeStatus.COMPLETED -> animeColors.finishedContainer
        AnimeStatus.PLANNED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
        AnimeStatus.DROPPED -> animeColors.droppedContainer
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.add_anime_watch_status),
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
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = SquircleShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = statusColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedLabelColor = statusColor,
                    focusedContainerColor = statusContainerColor,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = statusColor
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = SquircleShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                AnimeStatus.entries.forEach { status ->
                    val isSelected = status == selectedStatus
                    val itemColor = when (status) {
                        AnimeStatus.WATCHING -> animeColors.watching
                        AnimeStatus.COMPLETED -> animeColors.finished
                        AnimeStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
                        AnimeStatus.DROPPED -> animeColors.dropped
                    }
                    val itemContainerColor = when (status) {
                        AnimeStatus.WATCHING -> animeColors.watchingContainer
                        AnimeStatus.COMPLETED -> animeColors.finishedContainer
                        AnimeStatus.PLANNED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        AnimeStatus.DROPPED -> animeColors.droppedContainer
                    }

                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelected) Modifier.background(itemContainerColor)
                                        else Modifier
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(itemColor, CircleShape)
                                    )
                                    Text(
                                        text = status.displayName,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (isSelected) itemColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = itemColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onStatusChange(status)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
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
    // rating 为 null 视为 0；用户拖动后即使为 0 也写入数据库
    val ratingValue = rating ?: 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.add_anime_rating),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (rating != null && rating > 0f) stringResource(R.string.add_anime_rating_value, rating)
                else stringResource(R.string.add_anime_no_rating),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (rating != null && rating > 0f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RatingBar(
                value = ratingValue,
                modifier = Modifier,
                config = RatingBarConfig()
                    .numStars(5)
                    .size(28.dp)
                    .padding(4.dp)
                    .style(RatingBarStyle.HighLighted)
                    .stepSize(StepSize.HALF)
                    .hideInactiveStars(false)
                    .isIndicator(false)
                    .activeColor(MaterialTheme.colorScheme.primary)
                    .inactiveColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                onValueChange = { newValue ->
                    onRatingChange(newValue)
                },
                onRatingChanged = { newValue ->
                    if (newValue <= 0f) onRatingChange(null)
                }
            )

            if (rating != null && rating > 0f) {
                TextButton(
                    onClick = { onRatingChange(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_clear),
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
    
    Column(modifier = modifier.fillMaxWidth()) {
        when (status) {
            AnimeStatus.WATCHING -> {
                Text(
                    text = stringResource(R.string.add_anime_watching_status),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            AnimeStatus.COMPLETED -> {
                DatePickerField(
                    label = stringResource(R.string.add_anime_finish_date),
                    date = finishDate,
                    onClick = { showFinishDatePicker = true },
                    onClear = { onFinishDateChange(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimeStatus.DROPPED -> {
                DatePickerField(
                    label = stringResource(R.string.add_anime_dropped_date),
                    date = finishDate,
                    onClick = { showFinishDatePicker = true },
                    onClear = { onFinishDateChange(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimeStatus.PLANNED -> {
                Text(
                    text = stringResource(R.string.add_anime_planned_status),
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
                    Text(stringResource(R.string.common_ok))
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
                    Text(stringResource(R.string.common_ok))
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
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = if (date != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (date != null) formatDate(date) else stringResource(R.string.add_anime_select_date),
                    fontSize = 16.sp,
                    color = if (date != null) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (date != null) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.common_clear),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
