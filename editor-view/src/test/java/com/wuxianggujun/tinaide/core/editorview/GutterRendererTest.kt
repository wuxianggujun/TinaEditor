package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GutterRendererTest {
    private val documents = mutableListOf<EditorState>()
    private val renderer = GutterRenderer(18f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    @After
    fun closeDocuments() = documents.forEach { it.close(); it.textBuffer.close() }

    @Test
    fun eachLogicalLineRendersItsOwnColoredLabel() {
        val state = document("info\nwarning\nerror")
        state.gutterDecorations[0] = label('I', Color.Red)
        state.gutterDecorations[1] = label('W', Color.Blue)
        state.gutterDecorations[2] = label('E', Color.Green)
        val image = labels(state)
        assertThat(hasColor(image, 0, 24, android.graphics.Color.RED)).isTrue()
        assertThat(hasColor(image, 24, 48, android.graphics.Color.BLUE)).isTrue()
        assertThat(hasColor(image, 48, 72, android.graphics.Color.GREEN)).isTrue()
        assertThat(hasInk(image, 72, HEIGHT)).isFalse()
    }

    @Test
    fun softWrappedContinuationsDoNotRepeatTheLogicalLineLabel() {
        val state = document("x".repeat(40), wrap = true)
        state.gutterDecorations[0] = label('W', Color.Yellow)
        assertThat(state.isVisualLineContinuation(1)).isTrue()
        val image = labels(state)
        assertThat(hasInk(image, 0, 24)).isTrue()
        assertThat(hasInk(image, 24, HEIGHT)).isFalse()
    }

    @Test
    fun verticalScrollingMovesTheRightLevelWithItsLine() {
        val state = document((0..12).joinToString("\n") { "line$it" })
        state.gutterDecorations[0] = label('I', Color.Red)
        state.gutterDecorations[1] = label('W', Color.Blue)
        state.scrollBy(24f)
        assertThat(state.scrollOffsetPx).isEqualTo(24f)
        val image = labels(state)
        assertThat(hasColor(image, 0, 24, android.graphics.Color.BLUE)).isTrue()
        assertThat(hasColor(image, 0, HEIGHT, android.graphics.Color.RED)).isFalse()
    }

    @Test
    fun defaultAndHiddenModesNeverRenderCustomLabels() {
        val state = document("line")
        state.gutterDecorations[0] = label('E', Color.Red)
        for (mode in listOf(EditorGutterMode.DEFAULT, EditorGutterMode.HIDDEN)) {
            state.config = state.config.copy(gutterMode = mode)
            assertThat(hasInk(labels(state), 0, HEIGHT)).isFalse()
        }
    }

    @Test
    fun clearingLabelMetadataRemovesTheVisibleMarker() {
        val state = document("line")
        state.gutterDecorations[0] = label('W', Color.Blue)
        assertThat(hasInk(labels(state), 0, HEIGHT)).isTrue()
        state.gutterDecorations.clear()
        assertThat(hasInk(labels(state), 0, HEIGHT)).isFalse()
    }

    @Test
    fun labelModeKeepsNumbersEvenIfBreakpointOrBookmarkMetadataIsPresent() {
        val state = document("one\ntwo")
        val lineNumberRenderer = LineNumberRenderer(4f, 4f)
        fun numbers() = snapshot {
            lineNumberRenderer.draw(this, state, paint, widthPx = 32f)
        }
        val plain = numbers()
        state.gutterDecorations[0] = GutterDecoration(breakpoint = true, bookmark = true, label = GutterLineLabel('E'))
        assertThat(numbers().contentEquals(plain)).isTrue()
        state.config = state.config.copy(gutterMode = EditorGutterMode.DEFAULT)
        assertThat(numbers().contentEquals(plain)).isFalse()
    }

    @Test
    fun bodyForegroundOverridesRenderAcrossSoftWrapAndInvalidateWhenColorsChange() {
        val state = document("warning ".repeat(20), wrap = true)
        val editor = EditorRenderer(LineNumberRenderer(4f, 4f), renderer, EditorLineLayoutCache(), 2f, 2f, 1f)
        val textPaint = Paint(paint).apply { textAlign = Paint.Align.LEFT }
        val zones = editor.hitZones(state, paint)
        state.updateMetrics(24f, 11f, HEIGHT.toFloat(), WIDTH - zones.textStartX, zones.textStartX)
        fun frame() = snapshot { editor.render(this, state, textPaint, paint) }
        state.lineForegroundColors[0] = Color.Red
        val red = frame()
        assertThat(hasColor(red, 0, 24, android.graphics.Color.RED)).isTrue()
        assertThat(hasColor(red, 24, 48, android.graphics.Color.RED)).isTrue()
        state.lineForegroundColors[0] = Color.Green
        val green = frame()
        assertThat(hasColor(green, 0, 48, android.graphics.Color.RED)).isFalse()
        assertThat(hasColor(green, 0, 48, android.graphics.Color.GREEN)).isTrue()
        state.lineForegroundColors.clear()
        assertThat(hasColor(frame(), 0, 48, android.graphics.Color.GREEN)).isFalse()
    }

    @Test
    fun actualEditorRendererKeepsTheCompactColumnPinnedWhileBodyScrolls() {
        val state = document((0..4).joinToString("\n") { "message$it " + "x".repeat(100) })
        repeat(5) { state.gutterDecorations[it] = label(if (it % 2 == 0) 'I' else 'W', Color.Blue) }
        val editor = EditorRenderer(LineNumberRenderer(4f, 4f), renderer, EditorLineLayoutCache(), 2f, 2f, 1f)
        val textPaint = Paint(paint).apply { textAlign = Paint.Align.LEFT }
        val zones = editor.hitZones(state, paint)
        state.updateMetrics(24f, 11f, HEIGHT.toFloat(), WIDTH - zones.textStartX, zones.textStartX)
        fun frame() = snapshot { editor.render(this, state, textPaint, paint) }
        val before = frame()
        state.scrollByX(80f)
        assertThat(state.scrollOffsetXPx).isGreaterThan(0f)
        val after = frame()
        val endColumn = zones.gutterEndX.toInt()
        repeat(HEIGHT) { y ->
            repeat(endColumn) { x -> assertThat(after[y * WIDTH + x]).isEqualTo(before[y * WIDTH + x]) }
        }
    }

    private fun document(text: String, wrap: Boolean = false) = EditorState(
        RopeTextBuffer(text), config = EditorConfig(
            wordWrap = wrap, pinLineNumber = true, codeFolding = false,
            rainbowBrackets = false, bracketPairGuides = false, gutterMode = EditorGutterMode.LABELS,
        ),
    ).apply {
        updateMetrics(24f, 11f, HEIGHT.toFloat(), 60f, 0f)
        documents.add(this)
    }

    private fun label(character: Char, color: Color) = GutterDecoration(label = GutterLineLabel(character, color))

    private fun labels(state: EditorState) = snapshot {
        renderer.drawLabels(this, state, paint, 0f,
            renderer.widthForMode(state.config.gutterMode, state.lineHeightPx, paint),
            state.visibleLines, state.scrollOffsetPx,
        )
    }

    private fun snapshot(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): IntArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        return try {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(WIDTH.toFloat(), HEIGHT.toFloat()), draw)
            IntArray(WIDTH * HEIGHT).also { bitmap.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun hasInk(image: IntArray, top: Int, bottom: Int) =
        (top * WIDTH until bottom * WIDTH).any { android.graphics.Color.alpha(image[it]) > 0 }

    private fun hasColor(image: IntArray, top: Int, bottom: Int, color: Int) =
        (top * WIDTH until bottom * WIDTH).any {
            android.graphics.Color.alpha(image[it]) > 0 && (image[it] and 0x00ffffff) == (color and 0x00ffffff)
        }

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 144
    }
}
