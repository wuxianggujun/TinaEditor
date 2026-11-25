package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/** Selection-only adapter: shares editor hit testing, ranges and handles, with no edit/IME host. */
internal class CodeViewerSelection(
    val state: EditorState,
    private val assembly: EditorRenderAssembly,
    private val density: Density,
) {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
    var menuVisible by mutableStateOf(false)
    var menuAnchor by mutableStateOf(Offset.Zero)

    fun dismiss() { menuVisible = false }

    fun selectAt(point: Offset): Boolean {
        if (state.textBuffer.length == 0) return false
        val offset = offsetAt(point) ?: return false
        val position = state.textBuffer.offsetToPosition(offset)
        val scrollX = state.scrollOffsetXPx
        val scrollY = state.scrollOffsetPx
        val selected = state.selectionRange
        if (selected == null || selected.isEmpty || offset !in selected.start until selected.end) {
            if (!state.selectWord(position.line, position.column)) {
                // Punctuation/emoji/whitespace also need to be copyable; the editor snaps UTF-16 boundaries.
                state.selectRangeFromHandleDrag(offset, (offset + 1).coerceAtMost(state.textBuffer.length))
            }
        }
        state.scrollByX(scrollX - state.scrollOffsetXPx)
        state.scrollBy(scrollY - state.scrollOffsetPx)
        menuAnchor = point
        menuVisible = true
        return true
    }

    fun extendTo(point: Offset) {
        val dragAnchor = state.selectionRange?.anchor ?: return
        val offset = offsetAt(point, clampToText = true) ?: return
        state.selectRangeFromHandleDrag(dragAnchor, offset)
        menuAnchor = point
        menuVisible = false
    }

    private fun offsetAt(point: Offset, clampToText: Boolean = false): Int? {
        val paint = assembly.textPaint
        paint.typeface = state.typeface
        paint.textSize = with(density) { state.fontSizeSp.sp.toPx() }
        val startX = state.contentStartXPx
        if (!clampToText && point.x + (if (state.pinLineNumber) 0f else state.scrollOffsetXPx) < startX) return null
        val visualLine = state.visualLineFromViewportY(point.y)
        val line = state.docLineForVisualLine(visualLine)
        val text = state.textBuffer.getLine(line)
        val startColumn = state.visualLineStartColumn(visualLine).coerceIn(0, text.length)
        val endColumn = state.visualLineEndColumn(visualLine).coerceIn(startColumn, text.length)
        val layout = assembly.lineLayoutCache.getPrefixLayout(state, line, text, state.textBuffer.version, paint)
        val x = (point.x - startX + state.scrollOffsetXPx).coerceAtLeast(0f) + layout.segmentStartAdvance(startColumn)
        val column = assembly.lineLayoutCache.xToColumn(layout, x).coerceIn(startColumn, endColumn)
        return state.textBuffer.positionToOffset(line, column)
    }
}

internal fun Modifier.codeViewerLongPress(selection: CodeViewerSelection): Modifier = pointerInput(selection) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val held = awaitLongPressOrCancellation(down.id)
        if (held != null && selection.selectAt(held.position)) {
            held.consume()
            var moved = false
            val completed = drag(held.id) { change ->
                if (moved || (change.position - held.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    selection.extendTo(change.position)
                }
                change.consume()
            }
            selection.menuVisible = completed
        } else if (held == null) {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && !up.isConsumed &&
                (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                selection.dismiss()
                selection.state.clearSelection()
            }
        }
    }
}

/** Compose Popup can be hosted inside a native PopupWindow; resolve the actual screen origin. */
internal class CodeViewerMenuPositionProvider(
    private val selection: CodeViewerSelection,
    private val hostView: View,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val coordinates = selection.coordinates?.takeIf { it.isAttached }
        val origin = coordinates?.localToScreen(Offset.Zero) ?: Offset.Zero
        val visible = Rect().also(hostView::getWindowVisibleDisplayFrame)
        val minX = visible.left + margin
        val minY = visible.top + margin
        val maxX = (visible.right - popupContentSize.width - margin).coerceAtLeast(minX)
        val maxY = (visible.bottom - popupContentSize.height - margin).coerceAtLeast(minY)
        val anchor = origin + selection.menuAnchor
        val above = anchor.y.roundToInt() - popupContentSize.height - margin
        val below = anchor.y.roundToInt() + selection.state.lineHeightPx.roundToInt() + margin
        return IntOffset(
            anchor.x.roundToInt().coerceIn(minX, maxX),
            (if (above >= minY) above else below).coerceIn(minY, maxY),
        )
    }
}
