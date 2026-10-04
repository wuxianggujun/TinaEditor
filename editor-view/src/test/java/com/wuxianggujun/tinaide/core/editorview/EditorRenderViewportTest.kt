package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EditorRenderViewportTest {
    @Test
    fun inverseViewport_coversPhysicalCanvasAtEveryScaleAndPivot() {
        val state = state()
        for (scale in listOf(0.25f, 0.5f, 1f, 2f, 4f)) {
            for (pivot in listOf(Offset.Zero, Offset(200f, 300f), Offset(400f, 600f))) {
                val viewport = EditorRenderViewport.forScalePreview(state, Size(400f, 600f), scale, pivot)
                assertThat(viewport.size.width * scale).isWithin(0.001f).of(400f)
                assertThat(viewport.size.height * scale).isWithin(0.001f).of(600f)
                assertThat(viewport.visibleLines.first * 20f - viewport.scrollOffsetPx).isAtMost(0f)
                val lastBottom = (viewport.visibleLines.last + 1) * 20f - viewport.scrollOffsetPx
                assertThat(lastBottom).isAtLeast(viewport.size.height)
            }
        }
    }

    @Test
    fun scrolledPreview_preservesFocusDocumentPositionWithoutMutatingState() {
        val state = state().apply { scrollOffsetPx = 1200f; scrollOffsetXPx = 800f }
        val pivot = Offset(200f, 300f)
        for (scale in listOf(0.5f, 2f)) {
            val viewport = EditorRenderViewport.forScalePreview(state, Size(400f, 600f), scale, pivot)
            assertThat(viewport.scrollOffsetPx + pivot.y / scale).isEqualTo(1500f)
            assertThat(viewport.scrollOffsetXPx + pivot.x / scale).isEqualTo(1000f)
        }
        assertThat(state.scrollOffsetPx).isEqualTo(1200f)
        assertThat(state.scrollOffsetXPx).isEqualTo(800f)
        assertThat(state.viewportHeightPx).isEqualTo(600f)
    }

    @Test
    fun zoomOutAtOrigin_doesNotCreateTopOrLeftBlankMargins() {
        val viewport = EditorRenderViewport.forScalePreview(state(), Size(400f, 600f), 0.25f, Offset(200f, 300f))
        assertThat(viewport.scrollOffsetPx).isEqualTo(0f)
        assertThat(viewport.scrollOffsetXPx).isEqualTo(0f)
        assertThat(viewport.visibleLines.first).isEqualTo(0)
    }

    @Test
    fun frameContext_returnsToNormalViewportAfterPreview() {
        val state = state().apply { scrollOffsetPx = 1200f }
        val context = EditorRenderFrameContext(TextRenderer())
        val scans = EditorTextScanCache()
        val brackets = EditorBracketSnapshotCache()
        val viewport = EditorRenderViewport.forScalePreview(state, Size(400f, 600f), 0.5f, Offset(200f, 300f))
        context.prepare(state, state.textVersion, scans, brackets, viewport)
        assertThat(context.visibleLines.first).isLessThan(state.visibleLines.first)
        assertThat(context.visibleLines.last).isGreaterThan(state.visibleLines.last)
        assertThat(context.visibleDocumentLines).isEqualTo(context.visibleLines)
        assertThat(context.visualLineTopInViewport(60)).isEqualTo(300f)
        context.prepare(state, state.textVersion, scans, brackets)
        assertThat(context.visibleLines).isEqualTo(state.visibleLines)
        assertThat(context.visualLineTopInViewport(60)).isEqualTo(0f)
    }

    @Test
    fun shortDocument_doesNotRequestRowsPastEnd() {
        val state = EditorState(RopeTextBuffer().apply { insert(0, "first\nlast") })
        state.updateMetrics(20f, 10f, 600f, 360f, 40f)
        val viewport = EditorRenderViewport.forScalePreview(state, Size(400f, 600f), 0.25f, Offset(200f, 300f))
        assertThat(viewport.visibleLines).isEqualTo(0..1)
    }

    @Test
    fun zoomAtDocumentEnd_shouldClampToPreviewBottomPaddingWithoutChangingState() {
        val state = state().apply { scrollOffsetPx = maxVerticalScrollOffsetPx() }
        val originalScroll = state.scrollOffsetPx
        val totalHeight = state.visualLineCount() * state.lineHeightPx
        for (scale in listOf(0.25f, 0.5f, 2f, 3f, 4f)) {
            val viewport = EditorRenderViewport.forScalePreview(
                state, Size(400f, 600f), scale, Offset(200f, 500f)
            )
            assertThat(viewport.visibleLines.isEmpty()).isFalse()
            assertThat(viewport.visibleLines.first).isAtLeast(0)
            assertThat(viewport.visibleLines.last).isLessThan(state.visualLineCount())
            assertThat(viewport.scrollOffsetPx)
                .isAtMost((totalHeight - viewport.size.height * 0.5f).coerceAtLeast(0f))
        }
        assertThat(state.scrollOffsetPx).isEqualTo(originalScroll)
        assertThat(state.viewportHeightPx).isEqualTo(600f)
        assertThat(state.maxVerticalScrollOffsetPx()).isEqualTo(originalScroll)
    }

    @Test
    fun zoomInOnShortOrEmptyDocument_shouldKeepFirstLineVisible() {
        for (text in listOf("", "first\nlast")) {
            val state = EditorState(RopeTextBuffer(text), config = EditorConfig(wordWrap = false))
            state.updateMetrics(20f, 10f, 600f, 360f, 40f)
            val viewport = EditorRenderViewport.forScalePreview(
                state, Size(400f, 600f), 4f, Offset(200f, 500f)
            )
            assertThat(viewport.scrollOffsetPx).isEqualTo(0f)
            assertThat(viewport.visibleLines).isEqualTo(0..(state.visualLineCount() - 1))
        }
    }

    private fun state() = EditorState(
        RopeTextBuffer().apply { insert(0, List(1000) { "row $it " + "text".repeat(100) }.joinToString("\n")) },
        config = EditorConfig(wordWrap = false)
    ).apply { updateMetrics(20f, 10f, 600f, 360f, 40f) }
}
