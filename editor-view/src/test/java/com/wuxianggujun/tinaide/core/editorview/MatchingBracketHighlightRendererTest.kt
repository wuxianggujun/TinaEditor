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
class MatchingBracketHighlightRendererTest {

    @Test
    fun resolveHighlightRects_shouldLookupSameLineTextOnce() {
        val env = createEnv("  call(foo)")
        val renderer = MatchingBracketHighlightRenderer()

        val rects = renderer.resolveHighlightRects(
            frameContext = env.frameContext,
            match = EditorBracketSnapshotCache.BracketMatch(
                openLine = 0,
                openColumn = 6,
                closeLine = 0,
                closeColumn = 10,
                depth = 1
            ),
            textStartX = env.textStartX,
            textPaint = env.textPaint,
            lineLayoutCache = env.lineLayoutCache
        )

        assertThat(rects).hasSize(2)
        assertThat(env.lineTextCalls[0]).isEqualTo(1)
        assertThat(rects[1].left).isGreaterThan(rects[0].left)
        assertThat(rects[0].top).isEqualTo(rects[1].top)
    }

    @Test
    fun resolveHighlightRects_shouldLookupEachLineOnceForCrossLineMatch() {
        val env = createEnv("{\n  call(\n)\n")
        val renderer = MatchingBracketHighlightRenderer()

        val rects = renderer.resolveHighlightRects(
            frameContext = env.frameContext,
            match = EditorBracketSnapshotCache.BracketMatch(
                openLine = 1,
                openColumn = 6,
                closeLine = 2,
                closeColumn = 0,
                depth = 1
            ),
            textStartX = env.textStartX,
            textPaint = env.textPaint,
            lineLayoutCache = env.lineLayoutCache
        )

        assertThat(rects).hasSize(2)
        assertThat(env.lineTextCalls[1]).isEqualTo(1)
        assertThat(env.lineTextCalls[2]).isEqualTo(1)
        assertThat(rects[1].top).isGreaterThan(rects[0].top)
    }

    @Test
    fun wrappedBrackets_shouldUseEachCharactersVisualRowAndSegmentAdvance() {
        val text = "a".repeat(30) + "(" + "b".repeat(29) + ")"
        val env = createEnv(text, wordWrap = true)
        val state = env.frameContext.state
        val rects = MatchingBracketHighlightRenderer().resolveHighlightRects(
            env.frameContext,
            EditorBracketSnapshotCache.BracketMatch(0, 30, 0, 60, 0),
            env.textStartX,
            env.textPaint,
            env.lineLayoutCache
        )
        val layout = env.lineLayoutCache.getPrefixLayout(
            state = state, line = 0, lineText = text,
            textVersion = state.textBuffer.version, paint = env.textPaint
        )

        assertThat(rects).hasSize(2)
        assertThat(rects[1].top).isGreaterThan(rects[0].top)
        listOf(30, 60).forEachIndexed { index, column ->
            val visualLine = state.visualLineForPosition(0, column)
            assertThat(visualLine).isGreaterThan(0)
            assertThat(rects[index].top).isEqualTo(state.visualLineTopInViewport(visualLine))
            val expectedLeft = env.textStartX + layout.textStartAdvance(column) -
                layout.segmentStartAdvance(state.visualLineStartColumn(visualLine))
            assertThat(rects[index].left).isWithin(0.001f).of(expectedLeft)
        }
        assertThat(env.lineTextCalls[0]).isEqualTo(1)
    }

    @Test
    fun wrappedBrackets_shouldSkipOffscreenSegmentButKeepVisibleCounterpart() {
        val env = createEnv("a".repeat(30) + "(" + "b".repeat(29) + ")", wordWrap = true)
        val state = env.frameContext.state
        state.updateMetrics(20f, 10f, 20f, 240f, 24f)
        val closeVisualLine = state.visualLineForPosition(0, 60)
        state.scrollOffsetPx = closeVisualLine * state.lineHeightPx

        val rects = MatchingBracketHighlightRenderer().resolveHighlightRects(
            env.frameContext,
            EditorBracketSnapshotCache.BracketMatch(0, 30, 0, 60, 0),
            env.textStartX, env.textPaint, env.lineLayoutCache
        )

        assertThat(rects).hasSize(1)
        assertThat(rects.single().top).isEqualTo(0f)
    }

    private fun createEnv(text: String, wordWrap: Boolean = false): TestEnv {
        val buffer = RopeTextBuffer().apply { insert(0, text) }
        val state = EditorState(
            textBuffer = buffer,
            config = EditorConfig(
                codeFolding = false,
                wordWrap = wordWrap,
                tabSize = 4
            )
        ).apply {
            typeface = Typeface.MONOSPACE
            updateMetrics(
                lineHeightPx = 20f,
                charWidthPx = 10f,
                viewportHeightPx = 240f,
                viewportWidthPx = 240f,
                contentStartXPx = 24f
            )
        }
        val lineTextCalls = linkedMapOf<Int, Int>()
        val lineTextProvider: (Int) -> String = { line ->
            lineTextCalls[line] = lineTextCalls.getOrDefault(line, 0) + 1
            buffer.getLine(line)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = state.typeface
            textSize = state.fontSizeSp
        }

        return TestEnv(
            textStartX = 24f,
            textPaint = textPaint,
            lineLayoutCache = EditorLineLayoutCache(),
            frameContext = EditorRenderFrameContext(
                state = state,
                textVersion = buffer.version,
                textScanCache = EditorTextScanCache(),
                bracketSnapshotCache = EditorBracketSnapshotCache(),
                lineTextProvider = lineTextProvider
            ),
            lineTextCalls = lineTextCalls
        )
    }

    private data class TestEnv(
        val textStartX: Float,
        val textPaint: Paint,
        val lineLayoutCache: EditorLineLayoutCache,
        val frameContext: EditorRenderFrameContext,
        val lineTextCalls: Map<Int, Int>
    )
}
