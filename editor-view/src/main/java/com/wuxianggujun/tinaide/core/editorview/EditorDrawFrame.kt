package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.wuxianggujun.tinaide.core.textengine.TextBufferClosedException

/** Covers metrics, text, minimap and cursor drawing, including scale previews. */
internal inline fun DrawScope.drawEditorFrame(state: EditorState, drawContent: DrawScope.() -> Unit) {
    if (!state.textBuffer.isClosed) {
        try {
            drawContent()
            return
        } catch (failure: TextBufferClosedException) {
            // isClosed is only a snapshot. Rope reads synchronize with close and
            // throw this specific exception if close wins during the frame.
            if (!state.textBuffer.isClosed) throw failure
        }
    }
    // Also erase any partially drawn content if close happened during this frame.
    drawRect(state.colorScheme.background)
}
