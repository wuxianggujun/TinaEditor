package com.wuxianggujun.tinaide.core.editorview

/**
 * Editor state snapshot for observers that need the latest value rather than a one-shot event.
 */
data class EditorObservableState(
    val documentVersion: Long,
    val documentLength: Int,
    val cursorOffset: Int,
    val selectionRange: OffsetRange?,
    val scrollOffsetXPx: Float,
    val scrollOffsetPx: Float,
    val isFocused: Boolean,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val contentStartXPx: Float,
    val lineHeightPx: Float,
    val charWidthPx: Float,
)
