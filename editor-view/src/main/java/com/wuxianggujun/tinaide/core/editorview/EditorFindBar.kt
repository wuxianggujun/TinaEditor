package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun EditorFindEffect(state: EditorState) {
    LaunchedEffect(state) {
        // focusRequest also changes when close/reopen is coalesced into the same frame.
        // The new session still needs a search after discarding the previous worker result.
        snapshotFlow { listOf(state.find.visible, state.find.focusRequest, state.find.query, state.find.options, state.textVersion) }
            .collectLatest {
                if (state.find.visible) {
                    delay(120)
                    state.find.refreshAsync()
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorFindBar(
    state: EditorState,
    modifier: Modifier = Modifier,
    beforeEdit: () -> Unit = {},
    afterAction: () -> Unit = {},
    onDismiss: () -> Unit = { state.find.dismiss() }
) {
    val find = state.find
    if (!find.visible) return
    val focusRequester = remember { FocusRequester() }
    var queryFocused by remember { mutableStateOf(false) }
    var replacementFocused by remember { mutableStateOf(false) }
    val popupColors = rememberEditorPopupColors(state.colorScheme)
    LaunchedEffect(find.focusRequest) {
        beforeEdit()
        focusRequester.requestFocus()
    }
    fun navigate(backward: Boolean) {
        if (backward) find.previous() else find.next()
        afterAction()
    }
    fun replaceCurrent() {
        beforeEdit()
        find.replaceCurrent()
        afterAction()
    }
    EditorPopupTheme(popupColors) {
        BoxWithConstraints(modifier) {
            // Landscape + a tall IME may leave less height than two controls. Scroll
            // all fields together, keep close outside that scroll, and reserve code space
            // proportionally. A fixed minimum panel height would consume the whole pane.
            val panelMaxHeight = if (constraints.hasBoundedHeight)
                (maxHeight - minOf(64.dp, maxHeight * 0.35f)).coerceAtLeast(1.dp) else 320.dp
            Surface(
                modifier = Modifier.heightIn(max = panelMaxHeight).onPreviewKeyEvent { event ->
                    val ctrlShortcut = event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
                    if (event.type != KeyEventType.KeyDown) false else when {
                        event.key == Key.Escape -> { onDismiss(); true }
                        event.key == Key.F3 -> { navigate(event.isShiftPressed); true }
                        event.isEditorEnterKey() && queryFocused -> { navigate(event.isShiftPressed); true }
                        event.isEditorEnterKey() && replacementFocused -> {
                            if (event.isShiftPressed) navigate(true) else replaceCurrent()
                            true
                        }
                        ctrlShortcut && event.key == Key.F -> { focusRequester.requestFocus(); true }
                        ctrlShortcut && event.key == Key.H -> { find.show(replace = true); true }
                        else -> false
                    }
                },
                color = popupColors.containerColor,
                contentColor = popupColors.primaryTextColor
            ) {
                Box {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 56.dp, top = 4.dp, bottom = 4.dp)) {
                        EditorFindInput(
                            value = find.query, onValueChange = find::updateQuery,
                            modifier = Modifier.fillMaxWidth().onFocusChanged { queryFocused = it.isFocused }
                                .focusRequester(focusRequester).testTag("editor_find_query"),
                            label = stringResource(R.string.editor_find_query),
                            errorMessage = if (find.error == EditorFindError.InvalidQuery)
                                stringResource(R.string.editor_find_invalid_query) else null,
                            onSubmit = { navigate(false) }
                        )
                        // Keep all options discoverable: narrow panes wrap the option group
                        // below navigation instead of leaving it a tiny scrolling sliver.
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.editor_find_count,
                                        if (find.matchCount == 0) 0 else find.activeIndex + 1, find.matchCount),
                                    style = MaterialTheme.typography.labelMedium, color = popupColors.secondaryTextColor
                                )
                                IconButton(onClick = { navigate(true) }, enabled = find.matchCount > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.editor_find_previous))
                                }
                                IconButton(onClick = { navigate(false) }, enabled = find.matchCount > 0) {
                                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.editor_find_next))
                                }
                            }
                            EditorFindOptionsRow(find)
                        }
                        if (find.showReplacement) {
                            EditorFindInput(
                                value = find.replacement, onValueChange = { find.replacement = it },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { replacementFocused = it.isFocused }
                                    .testTag("editor_find_replacement"),
                                label = stringResource(R.string.editor_find_replacement),
                                errorMessage = if (find.error == EditorFindError.InvalidReplacement)
                                    stringResource(R.string.editor_find_invalid_replacement) else null,
                                imeAction = ImeAction.Done,
                                onSubmit = ::replaceCurrent
                            )
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = ::replaceCurrent, enabled = find.matchCount > 0) {
                                    Text(stringResource(R.string.editor_find_replace))
                                }
                                TextButton(onClick = { beforeEdit(); find.replaceAll(); afterAction() }, enabled = find.matchCount > 0,
                                    modifier = Modifier.testTag("editor_find_replace_all")) {
                                    Text(stringResource(R.string.editor_find_replace_all))
                                }
                            }
                        }
                        find.error?.let { error ->
                            Text(
                                stringResource(if (error == EditorFindError.InvalidQuery)
                                    R.string.editor_find_invalid_query else R.string.editor_find_invalid_replacement),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp).testTag("editor_find_close")) {
                        Icon(Icons.Default.Close, stringResource(R.string.editor_find_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorFindOptionsRow(find: EditorFindController, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(find.options.caseSensitive,
            { find.updateOptions(find.options.copy(caseSensitive = !find.options.caseSensitive)) },
            label = { Text(stringResource(R.string.editor_find_case)) })
        FilterChip(find.options.wholeWord,
            { find.updateOptions(find.options.copy(wholeWord = !find.options.wholeWord)) },
            label = { Text(stringResource(R.string.editor_find_word)) })
        FilterChip(find.options.regex,
            { find.updateOptions(find.options.copy(regex = !find.options.regex)) },
            label = { Text(stringResource(R.string.editor_find_regex)) })
        FilterChip(find.showReplacement, find::toggleReplacement,
            label = { Text(stringResource(R.string.editor_find_replace)) })
    }
}

@Composable
private fun EditorFindInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String?,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 44.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = label
                errorMessage?.let { error(it) }
            }
            .background(colors.surfaceContainerHigh, shape)
            .border(1.dp, when {
                errorMessage != null -> colors.error
                focused -> colors.primary
                else -> colors.outlineVariant
            }, shape),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() }),
        decorationBox = { input ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                input()
            }
        }
    )
}
