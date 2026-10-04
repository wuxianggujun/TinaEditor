package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun EditorFindEffect(state: EditorState) {
    LaunchedEffect(state) {
        snapshotFlow { listOf(state.find.visible, state.find.query, state.find.options, state.textVersion) }
            .collectLatest {
                if (state.find.visible) {
                    delay(120)
                    state.find.refreshAsync()
                }
            }
    }
}

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
    LaunchedEffect(find.focusRequest) {
        beforeEdit()
        focusRequester.requestFocus()
    }
    fun navigate(backward: Boolean) {
        if (backward) find.previous() else find.next()
        afterAction()
    }
    Surface(modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when {
            event.key == Key.Escape -> { onDismiss(); true }
            event.key == Key.Enter || event.key == Key.F3 -> { navigate(event.isShiftPressed); true }
            event.isCtrlPressed && event.key == Key.F -> { focusRequester.requestFocus(); true }
            event.isCtrlPressed && event.key == Key.H -> { find.show(replace = true); true }
            else -> false
        }
    }, color = state.colorScheme.gutterBackground, contentColor = state.colorScheme.foreground) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = find.query, onValueChange = find::updateQuery,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("editor_find_query"),
                    singleLine = true, label = { Text(stringResource(R.string.editor_find_query)) },
                    isError = find.error == EditorFindError.InvalidQuery
                )
                Text(stringResource(R.string.editor_find_count,
                    if (find.matchCount == 0) 0 else find.activeIndex + 1, find.matchCount))
                IconButton(onClick = { navigate(true) }, enabled = find.matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.editor_find_previous))
                }
                IconButton(onClick = { navigate(false) }, enabled = find.matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.editor_find_next))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.editor_find_close))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (find.showReplacement) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = find.replacement, onValueChange = { find.replacement = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.editor_find_replacement)) },
                        isError = find.error == EditorFindError.InvalidReplacement)
                    TextButton(onClick = { beforeEdit(); find.replaceCurrent(); afterAction() }, enabled = find.matchCount > 0) {
                        Text(stringResource(R.string.editor_find_replace))
                    }
                    TextButton(onClick = { beforeEdit(); find.replaceAll(); afterAction() }, enabled = find.matchCount > 0) {
                        Text(stringResource(R.string.editor_find_replace_all))
                    }
                }
            }
            find.error?.let { error ->
                Text(stringResource(if (error == EditorFindError.InvalidQuery)
                    R.string.editor_find_invalid_query else R.string.editor_find_invalid_replacement))
            }
        }
    }
}
