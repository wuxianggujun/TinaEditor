package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp

/**
 * Read-only code surface using the same renderer, styling and line caches as [TinaEditor].
 * It deliberately creates no input host, focus target, edit gestures or language-service callbacks.
 * The caller owns the document/highlighter and supplies a bounded viewport through [modifier].
 */
@Composable
fun TinaCodeViewer(state: EditorState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val assembly = rememberEditorRenderAssembly(density, LocalView.current, state.runtimeOptions)
    val paintMemo = remember { PaintApplyMemo() }
    val horizontal = rememberScrollableState { delta ->
        val before = state.scrollOffsetXPx
        state.scrollByX(-delta)
        before - state.scrollOffsetXPx
    }
    val vertical = rememberScrollableState { delta ->
        val before = state.scrollOffsetPx
        state.scrollBy(-delta)
        before - state.scrollOffsetPx
    }
    val accessibleCode = remember(state, state.textVersion) {
        AnnotatedString(state.textBuffer.substring(0, state.textBuffer.length))
    }
    EditorTextBufferRendererSyncEffect(state, assembly.renderer)
    Canvas(
        modifier = modifier
            .testTag("tina-code-viewer")
            .clipToBounds()
            .semantics {
                text = accessibleCode
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffsetXPx },
                    maxValue = { state.maxHorizontalScrollOffsetPx() },
                )
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffsetPx },
                    maxValue = { state.maxVerticalScrollOffsetPx() },
                )
                scrollBy { x, y ->
                    state.scrollByX(x)
                    state.scrollBy(y)
                    true
                }
            }
            .scrollable(horizontal, Orientation.Horizontal)
            .scrollable(vertical, Orientation.Vertical),
    ) {
        drawEditorFrame(state) {
            @Suppress("UNUSED_EXPRESSION")
            state.effectiveStylingVersion
            @Suppress("UNUSED_EXPRESSION")
            state.textVersion
            paintMemo.apply(
                textPaint = assembly.textPaint,
                lineNumberPaint = assembly.lineNumberPaint,
                typeface = state.typeface,
                textSizePx = with(density) { state.fontSizeSp.sp.toPx() },
                lineNumberForegroundArgb = state.colorScheme.lineNumberForeground.toArgb(),
            )
            val contentStart = assembly.renderer.contentStartX(state, assembly.lineNumberPaint)
            state.updateMetrics(
                lineHeightPx = paintMemo.lineHeightPx,
                charWidthPx = paintMemo.charWidthPx,
                viewportHeightPx = size.height.coerceAtLeast(1f),
                viewportWidthPx = (size.width - contentStart).coerceAtLeast(1f),
                contentStartXPx = contentStart,
            )
            assembly.renderer.render(this, state, assembly.textPaint, assembly.lineNumberPaint)
            val scrollbar = assembly.scrollbarRenderer.calculateLayout(state, size.width, size.height, density)
            assembly.scrollbarRenderer.draw(this, scrollbar, 0.7f, state.colorScheme, null)
        }
    }
}
