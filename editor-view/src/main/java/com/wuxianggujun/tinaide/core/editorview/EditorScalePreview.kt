package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawEditorScalePreview(
    renderer: EditorRenderEngine,
    state: EditorState,
    textPaint: Paint,
    lineNumberPaint: Paint,
    scale: Float,
    pivot: Offset,
    cursorOverlay: Boolean = false
) {
    val viewport = EditorRenderViewport.forScalePreview(state, size, scale, pivot)
    val canvas = drawContext.canvas
    val originalSize = size
    canvas.save()
    try {
        // Expanding only the Canvas matrix is insufficient: nested clips and row culling must
        // see the inverse-sized viewport as well. The physical parent clip remains unchanged.
        drawContext.size = viewport.size
        canvas.scale(scale, scale)
        if (cursorOverlay) {
            renderer.renderCursorOverlay(this, state, textPaint, lineNumberPaint, viewport)
        } else {
            renderer.render(this, state, textPaint, lineNumberPaint, viewport)
        }
    } finally {
        drawContext.size = originalSize
        canvas.restore()
    }
}
