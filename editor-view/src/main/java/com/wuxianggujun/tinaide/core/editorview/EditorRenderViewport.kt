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
            // = s * (document - previewScroll). Clamp at the document origin, not at the old clip.
            val scrollY = (state.scrollOffsetPx + pivot.y - pivot.y / scale).coerceAtLeast(0f)
            val scrollX = (state.scrollOffsetXPx + pivot.x - pivot.x / scale).coerceAtLeast(0f)
            val lineHeight = state.lineHeightPx.coerceAtLeast(1f)
            val first = floor(scrollY / lineHeight).toInt()
            val last = (first + (size.height / lineHeight).toInt() + 2)
                .coerceAtMost(state.visualLineCount() - 1)
            return EditorRenderViewport(size, scrollY, scrollX, first..last)
        }
    }
}
