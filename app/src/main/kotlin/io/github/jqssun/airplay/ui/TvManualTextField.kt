package io.github.jqssun.airplay.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** D-pad focus navigates; OK/Enter or a pointer click starts editing. */
@Composable
fun TvManualTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, singleLine: Boolean = false, isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var editing by remember { mutableStateOf(false) }
    var activating by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(interactions, enabled) {
        interactions.interactions.collect { if (enabled && it is PressInteraction.Release) editing = true }
    }
    LaunchedEffect(editing) {
        if (editing) { withFrameNanos { }; keyboard?.show() }
    }
    OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled,
        readOnly = !editing, singleLine = singleLine, isError = isError,
        label = label, supportingText = supportingText,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        interactionSource = interactions,
        modifier = modifier.onFocusChanged {
            if (!it.isFocused) { editing = false; activating = false }
        }.onPreviewKeyEvent {
            val confirm = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter
            if (enabled && confirm && (!editing || activating)) {
                if (it.type == KeyEventType.KeyDown) { activating = true; editing = true }
                else if (it.type == KeyEventType.KeyUp) activating = false
                true
            } else false
        })
}
