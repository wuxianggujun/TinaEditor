package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.floor

/** A draw-only viewport in the unchanged font/layout coordinate system. Never writes EditorState. */
internal data class EditorRenderViewport(
    val size: Size,
    val scrollOffsetPx: Float,
    val scrollOffsetXPx: Float,
    val visibleLines: IntRange
) {
    companion object {
        fun forScalePreview(
            state: EditorState,
            canvasSize: Size,
            scale: Float,
            pivot: Offset
        ): EditorRenderViewport {
            require(scale.isFinite() && scale > 0f)
            val size = Size(canvasSize.width / scale, canvasSize.height / scale)
            // s * (document - scroll) + pivot * (1 - s)
            // = s * (document - previewScroll). Use the preview viewport for both limits;
            // the old viewport's bottom padding can otherwise move every row offscreen.
            val maxScrollY = state.maxVerticalScrollOffsetPx(size.height)
            val scrollY = (state.scrollOffsetPx + pivot.y - pivot.y / scale).coerceIn(0f, maxScrollY)
            val scrollX = (state.scrollOffsetXPx + pivot.x - pivot.x / scale).coerceAtLeast(0f)
            val lineHeight = state.lineHeightPx.coerceAtLeast(1f)
            val maxVisualLine = state.visualLineCount() - 1
            val first = floor(scrollY / lineHeight).toInt().coerceIn(0, maxVisualLine.coerceAtLeast(0))
            val last = (first + (size.height / lineHeight).toInt() + 2)
                .coerceAtMost(maxVisualLine)
            return EditorRenderViewport(size, scrollY, scrollX, first..last)
        }
    }
}
