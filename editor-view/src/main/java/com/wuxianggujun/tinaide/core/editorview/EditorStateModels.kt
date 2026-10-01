package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class EditorDocumentState(initialVersion: Long) {
    var selectionSet by mutableStateOf(EditorSelectionSet.single())
    var primarySelectionActive by mutableStateOf(false)
    var textVersion by mutableStateOf(initialVersion)
}

@Stable
internal class EditorViewportState {
    var scrollOffsetPx by mutableStateOf(0f)
    var scrollOffsetXPx by mutableStateOf(0f)
    var viewportHeightPx by mutableStateOf(1f)
    var viewportWidthPx by mutableStateOf(1f)
    var contentStartXPx by mutableStateOf(0f)
    var lineHeightPx by mutableStateOf(1f)
    var charWidthPx by mutableStateOf(1f)
    var isFocused by mutableStateOf(false)
}
