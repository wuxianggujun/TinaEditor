package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min

class GutterRenderer(
    private val minWidthPx: Float
) {
    private val expandedPath = Path()
    private val collapsedPath = Path()
    private var templateIconBox = -1f
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelCharacter = CharArray(1)

    fun widthForLineHeight(lineHeightPx: Float): Float = maxOf(minWidthPx, lineHeightPx.coerceAtLeast(1f))

    fun width(state: EditorState): Float = when (state.config.gutterMode) {
        EditorGutterMode.DEFAULT -> widthForLineHeight(state.lineHeightPx)
        EditorGutterMode.HIDDEN -> 0f
        EditorGutterMode.LABELS -> labelWidth(state.charWidthPx)
    }

    internal fun widthForMode(mode: EditorGutterMode, lineHeightPx: Float, paint: Paint): Float = when (mode) {
        EditorGutterMode.DEFAULT -> widthForLineHeight(lineHeightPx)
        EditorGutterMode.HIDDEN -> 0f
        EditorGutterMode.LABELS -> labelWidth(paint.measureText("W"))
    }

    private fun labelWidth(glyphWidthPx: Float): Float {
        val glyphWidth = glyphWidthPx.coerceAtLeast(1f)
        return glyphWidth + 2f * maxOf(1f, glyphWidth * 0.25f)
    }

    fun draw(
        drawScope: DrawScope,
        state: EditorState,
        originX: Float,
        visibleLines: IntRange = state.visibleLines,
        scrollOffsetPx: Float = state.scrollOffsetPx
    ) {
        if (state.config.gutterMode != EditorGutterMode.DEFAULT) return
        val scheme = state.colorScheme
        val widthPx = width(state)
        val lineHeightPx = state.lineHeightPx
        val foldCenterX = originX + widthPx * 0.5f
        val iconBox = (min(widthPx, lineHeightPx) * 0.62f).coerceAtLeast(8f)
        val half = iconBox * 0.5f
        val foldingValid = state.folding.isFoldingDataValid()

        if (iconBox != templateIconBox) {
            templateIconBox = iconBox
            expandedPath.reset()
            expandedPath.moveTo(-half, -half)
            expandedPath.lineTo(half, -half)
            expandedPath.lineTo(0f, half)
            expandedPath.close()

            collapsedPath.reset()
            collapsedPath.moveTo(-half, -half)
            collapsedPath.lineTo(-half, half)
            collapsedPath.lineTo(half, 0f)
            collapsedPath.close()
        }

        visibleLines.forEach { visualLine ->
            val line = state.docLineForVisualLine(visualLine)
            if (line >= state.textBuffer.lineCount) return@forEach
            if (state.isVisualLineContinuation(visualLine)) return@forEach
            val decoration = state.gutterDecorations[line] ?: return@forEach
            if (!decoration.foldable || !foldingValid) return@forEach

            val centerY = visualLine * lineHeightPx - scrollOffsetPx + lineHeightPx / 2f
            val collapsed = state.folding.isFoldCollapsedAtLine(line)
            val hasHiddenDiagnostic = state.folding.hasHiddenDiagnosticsInFold(line)
            val foldColor = when {
                hasHiddenDiagnostic -> scheme.foldIconWarning
                collapsed -> scheme.foldIconCollapsed
                else -> scheme.foldIconExpanded
            }

            val templatePath = if (collapsed) collapsedPath else expandedPath
            drawScope.translate(left = foldCenterX, top = centerY) {
                drawPath(
                    path = templatePath,
                    color = foldColor,
                    style = Fill
                )
            }
        }
    }

    internal fun drawLabels(
        drawScope: DrawScope,
        state: EditorState,
        textPaint: Paint,
        originX: Float,
        widthPx: Float,
        visibleLines: IntRange,
        scrollOffsetPx: Float,
    ) {
        if (state.config.gutterMode != EditorGutterMode.LABELS || widthPx <= 0f) return
        labelPaint.textSize = textPaint.textSize
        labelPaint.typeface = textPaint.typeface
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.isFakeBoldText = true
        val lineHeight = state.lineHeightPx
        val centerX = originX + widthPx * 0.5f
        drawScope.clipRect(left = originX, right = originX + widthPx) {
            drawIntoCanvas { canvas ->
                visibleLines.forEach { visualLine ->
                    val line = state.docLineForVisualLine(visualLine)
                    if (line >= state.textBuffer.lineCount || state.isVisualLineContinuation(visualLine)) return@forEach
                    val label = state.gutterDecorations[line]?.label ?: return@forEach
                    labelCharacter[0] = label.character
                    labelPaint.color = (if (label.color == Color.Unspecified) state.colorScheme.lineNumberForeground else label.color).toArgb()
                    val baseline = visualLine * lineHeight - scrollOffsetPx + lineHeight * 0.78f
                    canvas.nativeCanvas.drawText(labelCharacter, 0, 1, centerX, baseline, labelPaint)
                }
            }
        }
    }
}
