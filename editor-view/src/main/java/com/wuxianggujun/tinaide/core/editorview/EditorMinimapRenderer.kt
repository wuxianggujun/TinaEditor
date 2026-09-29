package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.wuxianggujun.tinaide.core.treesitter.HighlightLineSegment
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class MinimapLayout(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rowHeightPx: Float,
    val rowCount: Int,
    val thumbTopPx: Float,
    val thumbHeightPx: Float
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    fun contains(position: Offset): Boolean =
        position.x in left..right && position.y in top..bottom

    fun thumbContains(position: Offset): Boolean =
        position.x in left..right && position.y in thumbTopPx..(thumbTopPx + thumbHeightPx)

    /**
     * 触摸点 → 视觉行。行几何与 thumb 同源（行 v 固定落在 top + v * rowHeightPx），
     * 所以点中哪一行就跳到哪一行，不需要滚动进度换算。
     */
    fun visualLineAt(position: Offset): Int =
        ((position.y - top) / rowHeightPx).roundToInt().coerceIn(0, (rowCount - 1).coerceAtLeast(0))
}

/**
 * 编辑器右侧的小地图：整篇文档按视觉行压缩成色块带，并叠加当前视口指示框。
 *
 * 布局与 [EditorScrollbarRenderer] 各自独立计算，但共用行几何（视觉行空间）与
 * [EditorScrollbarRenderer.VERTICAL_BAR_OCCUPIED_WIDTH_DP] 这一条右边界约定，
 * 所以小地图永远紧贴竖直滚动条左缘，两侧不需要互相引用。
 */
internal class EditorMinimapRenderer {

    companion object {
        const val WIDTH_DP = 40f
        const val SIDE_GAP_DP = 1f
        const val MIN_ROW_HEIGHT_PX = 1.5f
        const val ROW_HEIGHT_RATIO = 0.25f
        const val MAX_ROW_HEIGHT_RATIO = 0.5f
        const val CHAR_WIDTH_RATIO = 0.16f
        const val THUMB_MIN_HEIGHT_DP = 24f
        const val DIVIDER_WIDTH_PX = 1f

        /** 画布过窄时不显示小地图，避免挤压已经很紧张的文本区。 */
        const val MIN_CANVAS_WIDTH_DP = 120f

        /**
         * 逐行取语法 token 的行数上限，超过则按步长采样。TreeSitter 的逐行缓存按视口节流，
         * 全量查询会和它的驱逐策略冲突，反而把视口附近的热数据冲掉。
         */
        const val MAX_HIGHLIGHT_ROWS = 3000
    }

    private var dpCacheDensity = 0f
    private var dpWidth = 0f
    private var dpSideGap = 0f
    private var dpScrollbarOccupied = 0f
    private var dpThumbMinHeight = 0f
    private var dpMinCanvasWidth = 0f

    private val segmentPaint = Paint().apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint().apply { style = Paint.Style.FILL }

    private val rowsPicture = Picture()
    private var rowsCacheKey: MinimapRowsCacheKey? = null

    private fun ensureDpCache(density: Density) {
        val d = density.density
        if (d == dpCacheDensity) return
        dpCacheDensity = d
        dpWidth = WIDTH_DP * d
        dpSideGap = SIDE_GAP_DP * d
        dpScrollbarOccupied = EditorScrollbarRenderer.VERTICAL_BAR_OCCUPIED_WIDTH_DP * d
        dpThumbMinHeight = THUMB_MIN_HEIGHT_DP * d
        dpMinCanvasWidth = MIN_CANVAS_WIDTH_DP * d
    }

    fun calculateLayout(
        state: EditorState,
        canvasWidth: Float,
        canvasHeight: Float,
        density: Density
    ): MinimapLayout? {
        if (!state.config.showMinimap) return null
        ensureDpCache(density)
        if (canvasWidth < dpMinCanvasWidth) return null
        val maxScroll = state.maxVerticalScrollOffsetPx()
        if (maxScroll <= 0f) return null

        val rowCount = state.visualLineCount()
        if (rowCount <= 1) return null

        val width = dpWidth
        val left = (canvasWidth - dpScrollbarOccupied - dpSideGap - width).coerceAtLeast(0f)
        val availableHeight = canvasHeight.coerceAtLeast(1f)

        val baseRowHeight = (state.lineHeightPx * ROW_HEIGHT_RATIO).coerceAtLeast(MIN_ROW_HEIGHT_PX)
        val maxRowHeight = (state.lineHeightPx * MAX_ROW_HEIGHT_RATIO).coerceAtLeast(baseRowHeight)
        val fit = availableHeight / (rowCount * baseRowHeight)
        val rowHeightPx = (baseRowHeight * fit.coerceAtMost(1f)).coerceIn(MIN_ROW_HEIGHT_PX, maxRowHeight)
        val height = (rowCount * rowHeightPx).coerceAtMost(availableHeight)

        // thumb 与行几何同源（行 v 位于 top + v * rowHeightPx），直接由 scrollOffset 换算；
        // 只在 maxScroll 的 vh*0.5 底部留白区钳制，保证滑到底时 thumb 底边正好对齐小地图底边。
        val thumbHeight = (state.viewportHeightPx / state.lineHeightPx * rowHeightPx)
            .coerceIn(dpThumbMinHeight.coerceAtMost(height), height)
        val maxThumbTop = (height - thumbHeight).coerceAtLeast(0f)
        val thumbTop = (state.scrollOffsetPx / state.lineHeightPx * rowHeightPx).coerceIn(0f, maxThumbTop)

        return MinimapLayout(
            left = left,
            top = 0f,
            width = width,
            height = height,
            rowHeightPx = rowHeightPx,
            rowCount = rowCount,
            thumbTopPx = thumbTop,
            thumbHeightPx = thumbHeight
        )
    }

    fun draw(
        drawScope: DrawScope,
        layout: MinimapLayout,
        state: EditorState,
        colorScheme: EditorColorScheme
    ) {
        drawScope.drawRect(
            color = colorScheme.gutterBackground,
            topLeft = Offset(layout.left, layout.top),
            size = Size(layout.width, layout.height)
        )
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPicture(obtainRowsPicture(layout, state, colorScheme))
            paintThumb(canvas.nativeCanvas, layout, colorScheme)
        }
        drawScope.drawLine(
            color = colorScheme.gutterDivider,
            start = Offset(layout.left - DIVIDER_WIDTH_PX, layout.top),
            end = Offset(layout.left - DIVIDER_WIDTH_PX, layout.bottom),
            strokeWidth = DIVIDER_WIDTH_PX
        )
    }

    private fun obtainRowsPicture(
        layout: MinimapLayout,
        state: EditorState,
        colorScheme: EditorColorScheme
    ): Picture {
        val key = MinimapRowsCacheKey(
            textVersion = state.textBuffer.version,
            highlightVersion = state.highlightVersion,
            rowCount = layout.rowCount,
            wordWrap = state.config.wordWrap,
            left = layout.left,
            top = layout.top,
            width = layout.width,
            rowHeightPx = layout.rowHeightPx,
            charScale = state.charWidthPx * CHAR_WIDTH_RATIO,
            colorScheme = colorScheme
        )
        if (key != rowsCacheKey) {
            val recordWidth = ceil(layout.right).toInt().coerceAtLeast(1)
            val recordHeight = ceil(layout.bottom).toInt().coerceAtLeast(1)
            val canvas = rowsPicture.beginRecording(recordWidth, recordHeight)
            paintRows(canvas, layout, state, colorScheme)
            rowsPicture.endRecording()
            rowsCacheKey = key
        }
        return rowsPicture
    }

    internal fun paintRows(
        canvas: Canvas,
        layout: MinimapLayout,
        state: EditorState,
        colorScheme: EditorColorScheme
    ) {
        val highlighter = state.highlighter ?: return
        val rowHeight = layout.rowHeightPx
        val charScale = (state.charWidthPx * CHAR_WIDTH_RATIO).coerceAtLeast(0.5f)
        val right = layout.right
        val rowCount = layout.rowCount
        // 超长文档按步长采样，把逐行查询次数控制在 MAX_HIGHLIGHT_ROWS 内。
        val stride = if (rowCount > MAX_HIGHLIGHT_ROWS) {
            ceil(rowCount.toFloat() / MAX_HIGHLIGHT_ROWS).toInt()
        } else {
            1
        }
        val wordWrap = state.config.wordWrap
        var lastDocLine = -1
        var row = 0
        while (row < rowCount) {
            val docLine = state.docLineForVisualLine(row)
            val rowTop = layout.top + row * rowHeight
            val rowBottom = rowTop + rowHeight
            // 自动换行时，折行延续行不重复画整行的 token；步长采样时每行都画，否则采样行可能整行空白。
            val drawTokens = stride > 1 || !wordWrap || docLine != lastDocLine
            if (drawTokens) {
                paintLineSegments(
                    canvas = canvas,
                    segments = highlighter.getLineSegments(docLine),
                    colorScheme = colorScheme,
                    left = layout.left,
                    charScale = charScale,
                    right = right,
                    rowTop = rowTop,
                    rowBottom = rowBottom
                )
            }
            lastDocLine = docLine
            row += stride
        }
    }

    private fun paintLineSegments(
        canvas: Canvas,
        segments: List<HighlightLineSegment>,
        colorScheme: EditorColorScheme,
        left: Float,
        charScale: Float,
        right: Float,
        rowTop: Float,
        rowBottom: Float
    ) {
        if (segments.isEmpty()) return
        val syntax = colorScheme.syntax
        for (segment in segments) {
            val segLeft = left + segment.startColumn * charScale
            if (segLeft >= right) continue
            val segRight = (left + segment.endColumn * charScale).coerceAtMost(right)
            segmentPaint.color = syntax.colorOf(segment.type).toArgb()
            canvas.drawRect(segLeft, rowTop, segRight, rowBottom, segmentPaint)
        }
    }

    internal fun paintThumb(
        canvas: Canvas,
        layout: MinimapLayout,
        colorScheme: EditorColorScheme
    ) {
        thumbPaint.color = colorScheme.scrollbarThumb.toArgb()
        canvas.drawRect(
            layout.left,
            layout.thumbTopPx,
            layout.right,
            layout.thumbTopPx + layout.thumbHeightPx,
            thumbPaint
        )
    }

    private data class MinimapRowsCacheKey(
        val textVersion: Long,
        val highlightVersion: Long,
        val rowCount: Int,
        val wordWrap: Boolean,
        val left: Float,
        val top: Float,
        val width: Float,
        val rowHeightPx: Float,
        val charScale: Float,
        val colorScheme: EditorColorScheme
    )
}
