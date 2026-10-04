package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

enum class EditorRenderLayer { Background, Foreground }

fun interface EditorCustomRenderer {
    /** Draw only; do not edit editor state or retain this frame's context. */
    fun draw(scope: DrawScope, context: EditorRenderContext)
}

data class EditorRenderExtension(
    val renderer: EditorCustomRenderer,
    val layer: EditorRenderLayer = EditorRenderLayer.Background
)

data class EditorVisibleRow(
    val visualLine: Int,
    val documentLine: Int,
    val startOffset: Int,
    val endOffset: Int,
    val top: Float,
    val height: Float
)

/**
 * Read-only geometry valid only during draw(). Coordinates use the supplied DrawScope:
 * horizontal scrolling, clipping and pinch preview transforms are already applied.
 * Hidden folded lines have no rectangles; wrapped ranges produce one rectangle per row.
 */
class EditorRenderContext internal constructor(
    private val frame: EditorRenderFrameContext,
    private val textStartX: Float,
    private val paint: Paint,
    private val layouts: EditorLineLayoutCache
) {
    val documentVersion: Long = frame.textVersion
    val colorScheme: EditorColorScheme = frame.state.colorScheme
    val visibleRows: List<EditorVisibleRow> = frame.visibleLines.mapNotNull { visual ->
        val line = frame.state.docLineForVisualLine(visual)
        if (line !in 0 until frame.state.textBuffer.lineCount) return@mapNotNull null
        val start = frame.state.textBuffer.getLineStart(line)
        EditorVisibleRow(visual, line, start + frame.state.visualLineStartColumn(visual),
            start + frame.state.visualLineEndColumn(visual),
            frame.visualLineTopInViewport(visual), frame.state.lineHeightPx)
    }

    fun lineText(documentLine: Int): String = frame.lineText(documentLine)

    fun rangeRectangles(range: OffsetRange): List<Rect> = buildList {
        for (row in visibleRows) {
            if (range.end < row.startOffset || range.start > row.endOffset) continue
            val start = maxOf(range.start, row.startOffset)
            val end = minOf(range.end, row.endOffset)
            val state = frame.state
            val includesLineBreak = range.start <= row.endOffset && range.end > row.endOffset &&
                row.endOffset == state.textBuffer.getLineEnd(row.documentLine)
            if (start >= end && !range.isEmpty && !includesLineBreak) continue
            val lineStart = state.textBuffer.getLineStart(row.documentLine)
            if (range.isEmpty && state.visualLineForPosition(row.documentLine, start - lineStart) != row.visualLine) continue
            val layout = layouts.getPrefixLayout(state, row.documentLine, frame.lineText(row.documentLine),
                documentVersion, paint)
            val segment = layout.segmentStartAdvance((row.startOffset - lineStart).coerceIn(0, layout.length))
            val left = textStartX + layout.textStartAdvance((start - lineStart).coerceIn(0, layout.length)) - segment
            val right = textStartX + layout.textEndAdvance((end - lineStart).coerceIn(0, layout.length)) - segment
            val minimumWidth = when {
                range.isEmpty -> 2f
                includesLineBreak -> state.charWidthPx.coerceAtLeast(2f) / 2f
                else -> 0f
            }
            add(Rect(left, row.top, maxOf(right, left + minimumWidth), row.top + row.height))
        }
    }
}
