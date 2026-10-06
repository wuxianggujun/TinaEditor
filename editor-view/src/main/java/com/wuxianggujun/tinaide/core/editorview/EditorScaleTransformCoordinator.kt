package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.editorapi.EditorFontSize
import kotlin.math.floor

/**
 * Keep the font and wrap layout unchanged during a pinch, then commit the final geometry once.
 * The last preview is the source of the document anchor; host persistence runs after the commit.
 */
internal class EditorScaleTransformCoordinator(
    private val state: EditorState,
    private val ui: TinaEditorUiState,
    private val density: Density,
    private val textPaint: Paint,
    private val lineNumberPaint: Paint,
    private val renderer: EditorRenderEngine,
    private val lineLayoutCache: EditorLineLayoutCache,
    private val touchDiagnostics: EditorTouchDiagnostics,
    private val interactionController: EditorInteractionController,
    private val gestureHandler: EditorGestureHandler,
    private val fontScaleCoordinator: EditorFontScaleCoordinator,
    private val scrollbarVisibilityCoordinator: ScrollbarVisibilityCoordinator
) {
    private data class ScaleGesture(
        val fontSizeSp: Float,
        val fontSizePx: Float,
        val lineHeightPx: Float,
        val charWidthPx: Float,
        val contentStartXPx: Float,
        val focus: Offset,
        var targetFontSizeSp: Float = fontSizeSp
    )

    private val lineTextLookup = EditorLineTextLookup(state)
    private var gesture: ScaleGesture? = null

    @Suppress("UNUSED_PARAMETER")
    fun onScaleGesture(zoomChange: Float, panChange: Offset, rotationChange: Float) {
        if (!zoomChange.isFinite() || zoomChange <= 0f || zoomChange == 1f || state.textBuffer.isClosed) return
        val active = gesture ?: beginGesture().also { gesture = it }
        // A per-event epsilon loses slow pinches entirely. Accumulate every finite delta;
        // only an unchanged Float (including a clamped boundary) is a no-op.
        val target = (active.targetFontSizeSp.toDouble() * zoomChange.toDouble())
            .coerceIn(EditorFontSize.MIN_SP.toDouble(), EditorFontSize.MAX_SP.toDouble()).toFloat()
        if (target == active.targetFontSizeSp) return
        active.targetFontSizeSp = target
        // Use actual px conversion, including Android's non-linear accessibility font scaling.
        ui.scaleGestureVisualScale = with(density) { target.sp.toPx() } / active.fontSizePx

        interactionController.cancelPendingCompletionRequest()
        ui.setContextMenuVisible(false)
        scrollbarVisibilityCoordinator.trigger()
        gestureHandler.onScaleApplied()
        touchDiagnostics.logThrottled(EditorTouchLogCategory.SCALE, "scale-step", 120L) {
            "scaleVisual fontSp=${active.fontSizeSp}->$target scale=${ui.scaleGestureVisualScale} " +
                "focus=${active.focus} wrap=${state.config.wordWrap}"
        }
    }

    private fun beginGesture(): ScaleGesture {
        val fontPx = with(density) { state.fontSizeSp.sp.toPx() }
        textPaint.typeface = state.typeface
        lineNumberPaint.typeface = state.typeface
        textPaint.textSize = fontPx
        lineNumberPaint.textSize = fontPx
        val metrics = textPaint.fontMetrics
        val focus = ui.transformGestureFocus ?: Offset(ui.canvasWidthPx * 0.5f, ui.canvasHeightPx * 0.5f)
        ui.scaleGestureVisualPivotX = focus.x
        ui.scaleGestureVisualPivotY = focus.y
        if (state.config.wordWrap) state.freezeWordWrapLayoutIfNeeded()
        return ScaleGesture(
            fontSizeSp = state.fontSizeSp,
            fontSizePx = fontPx,
            lineHeightPx = (metrics.descent - metrics.ascent + metrics.leading).coerceAtLeast(1f),
            charWidthPx = textPaint.measureText("0").coerceAtLeast(1f),
            contentStartXPx = renderer.contentStartX(state, lineNumberPaint),
            focus = focus
        )
    }

    fun onTransformFinished() = applyFinalScaleAndScroll()

    private fun applyFinalScaleAndScroll() {
        val active = gesture ?: return
        try {
            if (state.textBuffer.isClosed || active.targetFontSizeSp == active.fontSizeSp) return
            val scale = ui.scaleGestureVisualScale
            val preview = EditorRenderViewport.forScalePreview(
                state, Size(ui.canvasWidthPx, ui.canvasHeightPx), scale, active.focus
            )
            // Capture with the OLD Paint, scroll and frozen wrap map, not the partially updated layout.
            val wrapAnchor = if (state.config.wordWrap) captureWrapAnchor(active, preview, scale) else null

            val targetFontPx = with(density) { active.targetFontSizeSp.sp.toPx() }
            textPaint.textSize = targetFontPx
            lineNumberPaint.textSize = targetFontPx
            val targetMetrics = textPaint.fontMetrics
            val targetLineHeight = (targetMetrics.descent - targetMetrics.ascent + targetMetrics.leading).coerceAtLeast(1f)
            val targetCharWidth = textPaint.measureText("0").coerceAtLeast(1f)
            val targetContentStartX = renderer.contentStartX(state, lineNumberPaint)
            val targetViewportWidth = (ui.canvasWidthPx - targetContentStartX).coerceAtLeast(1f)
            val focus = active.focus
            val scrollY = (preview.scrollOffsetPx + focus.y / scale) *
                (targetLineHeight / active.lineHeightPx) - focus.y
            val previewFocusX = textViewportX(
                focus.x / scale, active.contentStartXPx, preview.size.width - active.contentStartXPx
            )
            val targetFocusX = textViewportX(focus.x, targetContentStartX, targetViewportWidth)
            val scrollX = if (state.config.wordWrap) 0f else
                (preview.scrollOffsetXPx + previewFocusX) * (targetCharWidth / active.charWidthPx) - targetFocusX

            fontScaleCoordinator.apply(active.targetFontSizeSp) {
                state.commitScaleGeometry(
                    lineHeightPx = targetLineHeight,
                    charWidthPx = targetCharWidth,
                    viewportWidthPx = targetViewportWidth,
                    contentStartXPx = targetContentStartX,
                    scrollOffsetPx = scrollY,
                    scrollOffsetXPx = scrollX,
                    wrapAnchor = wrapAnchor
                )
                ui.contentStartXPx = targetContentStartX
                ui.scaleGestureVisualScale = 1f
            }
            touchDiagnostics.log(EditorTouchLogCategory.SCALE,
                "scaleEnd fontSp=${active.fontSizeSp}->${state.fontSizeSp} focus=$focus " +
                    "scroll=(${state.scrollOffsetXPx},${state.scrollOffsetPx}) lineHeight=${state.lineHeightPx} " +
                    "wrap=${state.config.wordWrap}")
        } finally {
            // Also clean up a no-op, a closed document, or a throwing host callback.
            Snapshot.withMutableSnapshot {
                ui.scaleGestureVisualScale = 1f
                state.unfreezeWordWrapLayout()
            }
            gesture = null
        }
    }

    private fun textViewportX(canvasX: Float, contentStartX: Float, viewportWidth: Float): Float {
        val x = canvasX - contentStartX
        return if (state.pinLineNumber) x.coerceIn(0f, viewportWidth.coerceAtLeast(1f)) else x
    }

    private fun captureWrapAnchor(
        active: ScaleGesture,
        preview: EditorRenderViewport,
        scale: Float
    ): EditorState.ScaleAnchor? {
        val lineCount = state.textBuffer.lineCount
        if (lineCount <= 0) return null
        val contentY = preview.scrollOffsetPx + active.focus.y / scale
        val visualLine = floor(contentY / active.lineHeightPx).toInt()
            .coerceIn(0, (state.visualLineCount() - 1).coerceAtLeast(0))
        val docLine = state.docLineForVisualLine(visualLine).coerceIn(0, lineCount - 1)
        val text = lineTextLookup.lineText(docLine)
        val start = state.visualLineStartColumn(visualLine).coerceIn(0, text.length)
        val end = state.visualLineEndColumn(visualLine).coerceIn(start, text.length)
        val contentX = (preview.scrollOffsetXPx + active.focus.x / scale - active.contentStartXPx).coerceAtLeast(0f)
        val layout = lineLayoutCache.getPrefixLayout(
            state, docLine, text, state.textBuffer.version, textPaint, active.lineHeightPx
        )
        val column = lineLayoutCache.xToColumn(layout, layout.segmentStartAdvance(start) + contentX).coerceIn(start, end)
        return EditorState.ScaleAnchor(
            charOffset = state.textBuffer.positionToOffset(docLine, column),
            focusX = active.focus.x,
            focusY = active.focus.y,
            focusYInVisualLineRatio = ((contentY - visualLine * active.lineHeightPx) / active.lineHeightPx).coerceIn(0f, 1f)
        )
    }
}
