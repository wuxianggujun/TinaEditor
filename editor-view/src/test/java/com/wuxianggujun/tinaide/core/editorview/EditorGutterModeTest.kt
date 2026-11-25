package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.treesitter.TreeSitterFoldingProvider.FoldRegion
import io.mockk.mockk
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorGutterModeTest {
    private val documents = mutableListOf<EditorState>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    @After
    fun closeDocuments() = documents.forEach { it.close(); it.textBuffer.close() }

    @Test
    fun defaultRetainsTheExistingDecorationColumn() {
        val state = document()
        val zones = renderer().hitZones(state, paint)
        assertThat(state.config.gutterMode).isEqualTo(EditorGutterMode.DEFAULT)
        assertThat(zones.gutterEndX - zones.lineNumberEndX).isEqualTo(18f)
        assertThat(zones.textStartX - zones.gutterEndX).isEqualTo(5f)
    }

    @Test
    fun hiddenRemovesTheExtraColumnWithoutRemovingLineNumbers() {
        val zones = renderer().hitZones(document(EditorGutterMode.HIDDEN), paint)
        assertThat(zones.lineNumberEndX).isGreaterThan(0f)
        assertThat(zones.gutterEndX).isEqualTo(zones.lineNumberEndX)
    }

    @Test
    fun labelsUseOneGlyphWithTightPaddingRatherThanTheMinimumFoldingWidth() {
        val zones = renderer().hitZones(document(EditorGutterMode.LABELS), paint)
        val width = zones.gutterEndX - zones.lineNumberEndX
        assertThat(width).isAtLeast(paint.measureText("W"))
        assertThat(width).isLessThan(18f)
        assertThat(zones.lineNumberEndX).isGreaterThan(0f)
    }

    @Test
    fun changingModeInvalidatesTheSameRenderersHitZoneCacheImmediately() {
        val state = document()
        val renderer = renderer()
        val original = renderer.hitZones(state, paint)
        state.config = state.config.copy(gutterMode = EditorGutterMode.LABELS)
        val labels = renderer.hitZones(state, paint)
        assertThat(labels.textStartX).isLessThan(original.textStartX)
        state.config = state.config.copy(gutterMode = EditorGutterMode.HIDDEN)
        assertThat(renderer.hitZones(state, paint).gutterEndX).isEqualTo(labels.lineNumberEndX)
        state.config = state.config.copy(gutterMode = EditorGutterMode.DEFAULT)
        assertThat(renderer.hitZones(state, paint)).isEqualTo(original)
    }

    @Test
    fun labelWidthTracksFontSizeAndTypefaceWithoutAddingPerLineLayoutWork() {
        val state = document(EditorGutterMode.LABELS)
        val renderer = renderer()
        renderer.hitZones(state, paint)
        paint.textSize = 30f
        paint.typeface = Typeface.SERIF
        assertThat(renderer.hitZones(state, paint)).isEqualTo(renderer().hitZones(state, paint))
        assertThat(renderer.performanceSnapshot().lineLayoutCacheEntryCount).isEqualTo(0)
    }

    @Test
    fun nonInteractiveModesIgnoreDirectGutterCallbacks() {
        for (mode in listOf(EditorGutterMode.HIDDEN, EditorGutterMode.LABELS)) {
            val state = document(mode)
            var calls = 0
            state.gutterDecorations[0] = GutterDecoration(foldable = true)
            state.onGutterTap = { calls++ }
            state.onGutterFoldToggle = { calls++ }
            state.onGutterLongPress = { calls++ }
            state.dispatchGutterTap(0)
            state.dispatchGutterFoldToggle(0)
            state.dispatchGutterLongPress(0)
            assertThat(calls).isEqualTo(0)
        }
    }

    @Test
    fun tapsAndLongPressesOnNumbersAndLabelsCannotTriggerEditorActions() {
        for (mode in listOf(EditorGutterMode.HIDDEN, EditorGutterMode.LABELS)) {
            val state = document(mode)
            var calls = 0
            state.onLineNumberTap = { calls++ }
            state.onLineNumberLongPress = { calls++ }
            state.onGutterTap = { calls++ }
            state.onGutterLongPress = { calls++ }
            val renderer = renderer()
            val coordinator = coordinator(state, renderer)
            val zones = renderer.hitZones(state, paint)
            coordinator.onTap(Offset(1f, 5f), isAltPressed = true)
            coordinator.onLongPress(Offset(1f, 5f))
            if (mode == EditorGutterMode.LABELS) {
                val label = Offset((zones.lineNumberEndX + zones.gutterEndX) / 2f, 5f)
                coordinator.onTap(label)
                coordinator.onLongPress(label)
            }
            assertThat(calls).isEqualTo(0)
            assertThat(state.selectionSet.selections).hasSize(1)
        }
    }

    @Test
    fun defaultStillDispatchesNormalLineNumberAndGutterActions() {
        val state = document()
        var calls = 0
        state.onLineNumberTap = { calls++ }
        state.onLineNumberLongPress = { calls++ }
        state.onGutterTap = { calls++ }
        state.onGutterLongPress = { calls++ }
        val renderer = renderer()
        val coordinator = coordinator(state, renderer)
        val zones = renderer.hitZones(state, paint)
        coordinator.onTap(Offset(1f, 5f))
        coordinator.onLongPress(Offset(1f, 5f))
        val gutter = Offset((zones.lineNumberEndX + zones.gutterEndX) / 2f, 5f)
        coordinator.onTap(gutter)
        coordinator.onLongPress(gutter)
        assertThat(calls).isEqualTo(4)
    }

    @Test
    fun refreshingFoldsPreservesAnIndependentLineLabel() {
        val state = document(EditorGutterMode.LABELS)
        val label = GutterLineLabel('W')
        state.gutterDecorations[0] = GutterDecoration(label = label)
        state.folding.setFoldRegions(listOf(FoldRegion(0, 2)), state.textBuffer.version)
        assertThat(state.gutterDecorations[0]?.foldable).isTrue()
        state.folding.clearFoldRegions()
        assertThat(state.gutterDecorations[0]?.foldable).isFalse()
        assertThat(state.gutterDecorations[0]?.label).isSameInstanceAs(label)
    }

    private fun document(mode: EditorGutterMode = EditorGutterMode.DEFAULT) = EditorState(
        RopeTextBuffer("one\ntwo\nthree"), config = EditorConfig(wordWrap = false, fontSizeSp = 11f, gutterMode = mode),
    ).apply {
        updateMetrics(20f, 8f, 100f, 200f, 0f)
        documents.add(this)
    }

    private fun renderer() = EditorRenderer(
        LineNumberRenderer(4f, 4f), GutterRenderer(18f), EditorLineLayoutCache(), 2f, 2f, 1f,
    )

    private fun coordinator(state: EditorState, renderer: EditorRenderer) = EditorGestureCoordinator(
        state = state,
        renderer = renderer,
        lineNumberPaint = paint,
        textPaint = paint,
        density = Density(1f),
        lineLayoutCache = EditorLineLayoutCache(),
        gestureHandler = EditorGestureHandler(uptimeMillisProvider = { 1_000L }),
        transformableState = TransformableState { _, _, _ -> },
        interactionController = mockk(relaxed = true),
        onContextMenuVisibleChange = {},
        onContextMenuOffsetChange = {},
    )
}
