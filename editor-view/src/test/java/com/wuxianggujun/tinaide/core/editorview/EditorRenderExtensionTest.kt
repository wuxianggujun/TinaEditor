package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import android.graphics.Typeface
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorRenderExtensionTest {
    @Test fun wrappedRangeIsSplitAndEachRowUsesItsOwnPrefixOrigin() {
        val state = state("a".repeat(80), wrap = true)
        val context = context(state)
        val rects = context.rangeRectangles(OffsetRange(0, 80))
        assertThat(rects.size).isGreaterThan(1)
        rects.forEach { assertThat(it.left).isWithin(0.01f).of(24f) }
        assertThat(rects.map { it.top }.distinct()).hasSize(rects.size)
    }

    @Test fun zeroWidthAtWrapBoundaryBelongsToOnlyOneVisualRow() {
        val state = state("a".repeat(80), wrap = true)
        val boundary = state.visualLineStartColumn(1)
        val rects = context(state).rangeRectangles(OffsetRange(boundary, boundary))
        assertThat(rects).hasSize(1)
        assertThat(rects.single().width).isAtLeast(2f)
        assertThat(rects.single().top).isEqualTo(state.visualLineTopInViewport(1))
    }

    @Test fun offscreenRangesDoNotProduceRectangles() {
        val state = state((1..30).joinToString("\n") { "abc" })
        state.scrollOffsetPx = 20f * 20
        assertThat(context(state).rangeRectangles(OffsetRange(0, 3))).isEmpty()
    }

    @Test fun newlineOnlyMatchesStillHaveAVisibleMarker() {
        val rects = context(state("abc\ndef")).rangeRectangles(OffsetRange(3, 4))
        assertThat(rects).hasSize(1)
        assertThat(rects.single().width).isGreaterThan(0f)
    }

    private fun state(text: String, wrap: Boolean = false) = EditorState(
        RopeTextBuffer().apply { insert(0, text) }, config = EditorConfig(wordWrap = wrap, codeFolding = false)
    ).apply { updateMetrics(20f, 10f, 240f, 240f, 24f) }

    private fun context(state: EditorState): EditorRenderContext {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = state.fontSizeSp
        }
        val frame = EditorRenderFrameContext(state, state.textBuffer.version,
            EditorTextScanCache(), EditorBracketSnapshotCache(), state.textBuffer::getLine)
        return EditorRenderContext(frame, 24f, paint, EditorLineLayoutCache())
    }
}
