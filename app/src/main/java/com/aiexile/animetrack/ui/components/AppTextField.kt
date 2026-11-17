package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private val AppTextFieldShape = SquircleShape(12.dp)

/**
 * 全局统一输入框：在对应 Material3 / Foundation 输入框基础上，增加「点击进入光标贴尾」行为。
 *
 * 焦点从无到有时（即点击输入框），把光标移动到文本末尾，而不是落在点击位置；
 * 默认沿用项目的 12dp 圆角；其余外观与参数转发给原组件。
 */

/**
 * 输入框内部状态：持有 [TextFieldValue] 以便控制光标位置。
 * [text] 为外部唯一数据源，内部文本与其不一致时（如清空、回填）整体同步并贴尾。
 */
@Stable
private class CursorEndTextFieldState(initialText: String) {
    var value by mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
        private set

    var isFocused by mutableStateOf(false)
        private set

    fun syncText(text: String) {
        if (value.text == text) return
        value = TextFieldValue(text, TextRange(text.length))
    }

    fun updateFromInput(newValue: TextFieldValue) {
        value = newValue
    }

    fun updateFocus(focused: Boolean) {
        isFocused = focused
    }

    fun moveCursorToEnd() {
        value = value.copy(selection = TextRange(value.text.length))
    }
}

@Composable
private fun rememberCursorEndTextFieldState(text: String): CursorEndTextFieldState {
    val state = remember { CursorEndTextFieldState(text) }
    state.syncText(text)
    return state
}

/** 焦点进入后（在点击手势处理完之后）再把光标移到末尾，避免被点击落点的选区覆盖。 */
@Composable
private fun CursorEndFocusEffect(state: CursorEndTextFieldState) {
    LaunchedEffect(state.isFocused) {
        if (state.isFocused) state.moveCursorToEnd()
    }
}

@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = AppTextFieldShape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val state = rememberCursorEndTextFieldState(value)
    CursorEndFocusEffect(state)
    OutlinedTextField(
        value = state.value,
        onValueChange = { newValue ->
            state.updateFromInput(newValue)
            if (newValue.text != value) onValueChange(newValue.text)
        },
        modifier = modifier.onFocusChanged { state.updateFocus(it.isFocused) },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = AppTextFieldShape,
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    val state = rememberCursorEndTextFieldState(value)
    CursorEndFocusEffect(state)
    TextField(
        value = state.value,
        onValueChange = { newValue ->
            state.updateFromInput(newValue)
            if (newValue.text != value) onValueChange(newValue.text)
        },
        modifier = modifier.onFocusChanged { state.updateFocus(it.isFocused) },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@Composable
fun AppBasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() }
) {
    val state = rememberCursorEndTextFieldState(value)
    CursorEndFocusEffect(state)
    BasicTextField(
        value = state.value,
        onValueChange = { newValue ->
            state.updateFromInput(newValue)
            if (newValue.text != value) onValueChange(newValue.text)
        },
        modifier = modifier.onFocusChanged { state.updateFocus(it.isFocused) },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox
    )
}
