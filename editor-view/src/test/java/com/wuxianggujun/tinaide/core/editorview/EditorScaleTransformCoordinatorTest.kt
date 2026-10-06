package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EditorScaleTransformCoordinatorTest {
    private val buffers = mutableListOf<RopeTextBuffer>()

    @After
    fun tearDown() {
        buffers.forEach { it.close() }
    }

    @Test
    fun release_publishesOnlyAfterFontMetricsScrollAndPreviewHaveCommitted() {
        val fixture = fixture()
        fixture.scale(1.1375f)
        fixture.release()

        val published = fixture.commits.single()
        assertThat(published.fontSize).isEqualTo(fixture.state.fontSizeSp)
        assertThat(published.lineHeight).isEqualTo(fixture.state.lineHeightPx)
        assertThat(published.charWidth).isEqualTo(fixture.state.charWidthPx)
        assertThat(published.scrollY).isEqualTo(fixture.state.scrollOffsetPx)
        assertThat(published.scrollX).isEqualTo(fixture.state.scrollOffsetXPx)
        assertThat(published.previewScale).isEqualTo(1f)
        assertThat(published.frozen).isFalse()
        assertThat(published.observable).isEqualTo(fixture.state.observableState.value)
    }

    @Test
    fun release_commitsSmallFractionalChangeInsteadOfSnappingBack() {
        val fixture = fixture()
        fixture.scale(1.004f)
        val expectedSize = 16f * 1.004f
        assertThat(fixture.ui.scaleGestureVisualScale).isGreaterThan(1f)

        fixture.release()

        assertThat(fixture.state.fontSizeSp).isWithin(0.00001f).of(expectedSize)
        assertThat(fixture.commits.single().fontSize).isEqualTo(fixture.state.fontSizeSp)
    }

    @Test
    fun smallGestureDeltas_accumulateRatherThanBeingDiscardedPerEvent() {
        val fixture = fixture()
        var expectedSize = 16f
        repeat(30) {
            fixture.scale(1.001f)
            expectedSize *= 1.001f
        }
        fixture.release()

        assertThat(fixture.state.fontSizeSp).isWithin(0.0001f).of(expectedSize)
    }

    @Test
    fun release_withoutWrap_preservesTheLastPreviewFocusAndDoesNotMoveAgainOnDraw() {
        for (pinned in listOf(false, true)) {
            for (scale in listOf(0.75f, 1.1375f, 2f)) {
                val fixture = fixture(pinLineNumbers = pinned)
                fixture.scale(scale)
                val preview = fixture.preview()
                val focusRow = (preview.scrollOffsetPx + fixture.focus.y / scale) / fixture.state.lineHeightPx
                val focusColumn = (preview.scrollOffsetXPx + fixture.focus.x / scale - fixture.state.contentStartXPx) /
                    fixture.state.charWidthPx

                fixture.release()

                assertThat(focusRow * fixture.state.lineHeightPx - fixture.state.scrollOffsetPx)
                    .isWithin(0.001f).of(fixture.focus.y)
                assertThat(fixture.state.contentStartXPx + focusColumn * fixture.state.charWidthPx - fixture.state.scrollOffsetXPx)
                    .isWithin(0.001f).of(fixture.focus.x)
                fixture.assertNextDrawIsStable()
            }
        }
    }

    @Test
    fun duplicateFinish_doesNotPublishOrCommitAgain() {
        val fixture = fixture()
        fixture.coordinator.onScaleGesture(1.125f, Offset.Zero, 0f)
        fixture.release()
        fixture.release()

        assertThat(fixture.state.fontSizeSp).isEqualTo(18f)
        assertThat(fixture.commits).hasSize(1)
    }

    @Test
    fun returningToInitialSize_andClosingDuringPinch_clearPreviewWithoutPublishing() {
        val fixture = fixture(wordWrap = true)
        fixture.scale(1.25f)
        fixture.scale(0.8f)
        fixture.release()
        assertThat(fixture.state.fontSizeSp).isEqualTo(16f)
        assertThat(fixture.ui.scaleGestureVisualScale).isEqualTo(1f)
        assertThat(fixture.state.isWordWrapLayoutFrozen()).isFalse()
        assertThat(fixture.commits).isEmpty()

        fixture.scale(1.5f)
        fixture.state.textBuffer.close()
        fixture.release()
        assertThat(fixture.ui.scaleGestureVisualScale).isEqualTo(1f)
        assertThat(fixture.state.isWordWrapLayoutFrozen()).isFalse()
        assertThat(fixture.commits).isEmpty()
    }

    @Test
    fun release_atDocumentEndWithKeyboardOverlap_usesTheEffectiveViewportForBothPhases() {
        for (height in listOf(320f, 600f)) {
            for (scale in listOf(0.5f, 0.997f, 1.5f, 3f)) {
                val fixture = fixture(viewportHeight = height)
                fixture.state.scrollOffsetPx = fixture.state.maxVerticalScrollOffsetPx()
                fixture.scale(scale)
                val preview = fixture.preview()
                val lastRow = fixture.state.visualLineCount() - 1
                val previewLastRowY = (lastRow * fixture.state.lineHeightPx - preview.scrollOffsetPx) *
                    fixture.ui.scaleGestureVisualScale

                fixture.release()

                val finalLastRowY = lastRow * fixture.state.lineHeightPx - fixture.state.scrollOffsetPx
                assertThat(finalLastRowY).isWithin(0.001f).of(previewLastRowY)
                fixture.assertNextDrawIsStable()
            }
        }
    }

    @Test
    fun throwingHostCallback_keepsCommittedGeometryAndDoesNotLeaveTheGestureFrozen() {
        val failure = IllegalStateException("Host persistence failed")
        val fixture = fixture(wordWrap = true, onCommit = { throw failure })
        fixture.scale(1.125f)

        assertThat(assertThrows(IllegalStateException::class.java) { fixture.release() }).isSameInstanceAs(failure)
        assertThat(fixture.state.fontSizeSp).isEqualTo(18f)
        assertThat(fixture.ui.scaleGestureVisualScale).isEqualTo(1f)
        assertThat(fixture.state.isWordWrapLayoutFrozen()).isFalse()
        fixture.assertNextDrawIsStable()
        fixture.release()
        assertThat(fixture.commits).hasSize(1)
    }

    @Test
    fun release_withWrap_reanchorsTheCharacterFromTheOldPreviewBeforePublishing() {
        for (scale in listOf(0.75f, 1.5f)) {
            val fixture = fixture(wordWrap = true)
            fixture.state.scrollOffsetXPx = 0f
            fixture.scale(scale)
            val preview = fixture.preview()
            val logicalFocusY = preview.scrollOffsetPx + fixture.focus.y / scale
            val visualLine = floor(logicalFocusY / fixture.state.lineHeightPx).toInt()
            val ratio = logicalFocusY / fixture.state.lineHeightPx - visualLine
            val docLine = fixture.state.docLineForVisualLine(visualLine)
            val text = fixture.state.textBuffer.getLine(docLine)
            val layout = fixture.cache.getPrefixLayout(fixture.state, docLine, text, fixture.state.textBuffer.version, fixture.paint)
            val start = fixture.state.visualLineStartColumn(visualLine)
            val end = fixture.state.visualLineEndColumn(visualLine)
            val contentX = (preview.scrollOffsetXPx + fixture.focus.x / scale - fixture.state.contentStartXPx).coerceAtLeast(0f)
            val column = fixture.cache.xToColumn(layout, layout.segmentStartAdvance(start) + contentX).coerceIn(start, end)

            fixture.release()

            val newVisualLine = fixture.state.visualLineForPosition(docLine, column)
            val finalY = (newVisualLine + ratio) * fixture.state.lineHeightPx - fixture.state.scrollOffsetPx
            assertThat(finalY).isWithin(0.01f).of(fixture.focus.y)
            assertThat(fixture.state.scrollOffsetXPx).isEqualTo(0f)
            assertThat(fixture.commits.single().frozen).isFalse()
            fixture.assertNextDrawIsStable()
        }
    }

    @Test
    fun repeatedGestures_supportSettingsBoundariesAndRejectInvalidZoom() {
        val fixture = fixture()
        fixture.scale(0.5f)
        fixture.release()
        assertThat(fixture.state.fontSizeSp).isEqualTo(8f)
        fixture.scale(6f)
        fixture.release()
        assertThat(fixture.state.fontSizeSp).isEqualTo(48f)
        for (zoom in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            fixture.scale(zoom)
            fixture.release()
            assertThat(fixture.state.fontSizeSp).isEqualTo(48f)
            assertThat(fixture.ui.scaleGestureVisualScale).isEqualTo(1f)
        }
        assertThat(fixture.commits).hasSize(2)
    }

    private fun fixture(
        wordWrap: Boolean = false,
        viewportHeight: Float = 600f,
        pinLineNumbers: Boolean = false,
        onCommit: (Float) -> Unit = {}
    ): Fixture {
        val buffer = RopeTextBuffer(List(200) { "abcdefghij".repeat(80) }.joinToString("\n"))
        buffers += buffer
        val density = Density(1f)
        val ui = TinaEditorUiState(density).apply {
            canvasWidthPx = 400f
            canvasHeightPx = 600f
            transformGestureFocus = Offset(220f, 280f)
        }
        val commits = mutableListOf<Commit>()
        lateinit var state: EditorState
        state = EditorState(
            buffer,
            config = EditorConfig(fontSizeSp = 16f, wordWrap = wordWrap, codeFolding = false, pinLineNumber = pinLineNumbers),
            runtimeOptions = EditorRuntimeOptions(onFontSizeChanged = { size ->
                commits += Commit(size, state.lineHeightPx, state.charWidthPx, state.scrollOffsetPx,
                    state.scrollOffsetXPx, ui.scaleGestureVisualScale, state.isWordWrapLayoutFrozen(),
                    state.observableState.value)
                onCommit(size)
            })
        )
        val paint = LinearMetricsPaint().apply { textSize = state.fontSizeSp }
        val lineNumberPaint = LinearMetricsPaint().apply { textSize = state.fontSizeSp }
        val cache = EditorLineLayoutCache()
        val renderer = EditorRenderer(LineNumberRenderer(4f, 4f), GutterRenderer(18f), cache, 2f, 2f, 1f)
        val contentStartX = renderer.contentStartX(state, lineNumberPaint)
        state.updateMetrics(16f, 8f, viewportHeight, 400f - contentStartX, contentStartX)
        state.scrollOffsetPx = 1000f
        state.scrollOffsetXPx = if (wordWrap) 0f else 300f
        val coordinator = EditorScaleTransformCoordinator(
            state, ui, density, paint, lineNumberPaint, renderer, cache,
            EditorTouchDiagnostics(), mockk(relaxed = true), EditorGestureHandler(),
            EditorFontScaleCoordinator(state), mockk(relaxed = true)
        )
        return Fixture(state, ui, paint, cache, coordinator, commits)
    }

    private data class Commit(
        val fontSize: Float,
        val lineHeight: Float,
        val charWidth: Float,
        val scrollY: Float,
        val scrollX: Float,
        val previewScale: Float,
        val frozen: Boolean,
        val observable: EditorObservableState
    )

    private data class Fixture(
        val state: EditorState,
        val ui: TinaEditorUiState,
        val paint: Paint,
        val cache: EditorLineLayoutCache,
        val coordinator: EditorScaleTransformCoordinator,
        val commits: List<Commit>
    ) {
        val focus: Offset get() = checkNotNull(ui.transformGestureFocus)

        fun scale(zoom: Float) {
            coordinator.onScaleGesture(zoom, Offset.Zero, 0f)
        }

        fun release() = coordinator.onTransformFinished()

        fun preview() = EditorRenderViewport.forScalePreview(
            state, Size(ui.canvasWidthPx, ui.canvasHeightPx), ui.scaleGestureVisualScale, focus
        )

        fun assertNextDrawIsStable() {
            val committed = state.observableState.value
            state.updateMetrics(state.lineHeightPx, state.charWidthPx, state.viewportHeightPx,
                state.viewportWidthPx, state.contentStartXPx)
            assertThat(state.observableState.value).isEqualTo(committed)
        }
    }

    // Deterministic geometry, not a GPU/font-rasterization test. The production coordinator,
    // renderer hit zones, prefix layout cache, viewport and wrap mapper are all used unchanged.
    private class LinearMetricsPaint : Paint() {
        override fun getFontMetrics(): FontMetrics = FontMetrics().also { getFontMetrics(it) }

        override fun getFontMetrics(metrics: FontMetrics?): Float {
            metrics?.apply {
                ascent = -textSize * 0.8f
                descent = textSize * 0.2f
                leading = 0f
                top = ascent
                bottom = descent
            }
            return textSize
        }

        override fun measureText(text: String): Float = text.length * textSize * 0.5f
        override fun measureText(text: String, start: Int, end: Int): Float = (end - start) * textSize * 0.5f
        override fun measureText(text: CharArray, index: Int, count: Int): Float = count * textSize * 0.5f

        override fun getTextRunAdvances(
            chars: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int,
            isRtl: Boolean, advances: FloatArray?, advancesIndex: Int
        ): Float {
            advances?.fill(textSize * 0.5f, advancesIndex, advancesIndex + count)
            return count * textSize * 0.5f
        }
    }
}
