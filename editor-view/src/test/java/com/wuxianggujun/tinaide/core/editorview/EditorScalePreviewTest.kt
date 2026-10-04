package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EditorScalePreviewTest {
    @Test
    fun zoomOut_drawsNewlyVisibleLinesBeforeRelease() = verifyCoverage(wordWrap = false)

    @Test
    fun zoomOut_drawsNewlyVisibleFrozenWrappedLinesBeforeRelease() = verifyCoverage(wordWrap = true)

    @Test
    fun preview_textAndCursorClipsCoverFullCanvasWithEitherGutterMode() {
        val state = EditorState(RopeTextBuffer().apply { insert(0, "content ".repeat(100)) }).apply {
            updateMetrics(24f, 12f, 480f, 280f, 40f)
            isFocused = true
        }
        val renderer = EditorRenderer(LineNumberRenderer(4f, 4f), GutterRenderer(18f), EditorLineLayoutCache(), 2f, 2f, 1f)
        for (pinned in listOf(false, true)) {
            state.pinLineNumber = pinned
            for (scale in listOf(0.25f, 0.5f, 2f, 4f)) {
                for (cursor in listOf(false, true)) {
                    val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
                    val canvas = ClipRecordingCanvas(bitmap)
                    val paint = Paint().apply { textSize = 24f }
                    try {
                        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(320f, 480f)) {
                            drawEditorScalePreview(renderer, state, paint, Paint(paint), scale, Offset(160f, 240f), cursor)
                            assertThat(size).isEqualTo(Size(320f, 480f))
                        }
                        assertThat(canvas.clips).isNotEmpty()
                        val clip = canvas.clips.first()
                        assertThat(clip.top).isEqualTo(0f)
                        assertThat(clip.right * scale).isWithin(0.001f).of(320f)
                        assertThat(clip.bottom * scale).isWithin(0.001f).of(480f)
                        val expectedLeft = if (pinned) renderer.contentStartX(state, paint) else 0f
                        assertThat(clip.left).isWithin(0.001f).of(expectedLeft)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun verifyCoverage(wordWrap: Boolean) {
        val state = EditorState(
            textBuffer = RopeTextBuffer().apply {
                insert(0, List(300) { "line_$it " + "content ".repeat(24) }.joinToString("\n"))
            },
            config = EditorConfig(wordWrap = wordWrap, rainbowBrackets = false, bracketPairGuides = false)
        ).apply {
            fontSizeSp = 24f
            updateMetrics(24f, 12f, 480f, 280f, 40f)
            if (wordWrap) freezeWordWrapLayoutIfNeeded()
        }
        val renderer = EditorRenderer(LineNumberRenderer(4f, 4f), GutterRenderer(18f), EditorLineLayoutCache(), 2f, 2f, 1f)
        draw(renderer, state, scale = 1f)
        val originalLines = renderer.performanceSnapshot().lastVisibleLineCount
        assertThat(originalLines).isGreaterThan(0)

        val previewBaselines = draw(renderer, state, scale = 0.5f)
        assertThat(renderer.performanceSnapshot().lastVisibleLineCount).isGreaterThan(originalLines * 3 / 2)
        // Check real text draw calls as well as metrics, so a stale culling loop cannot pass.
        assertThat(previewBaselines.maxOrNull()).isGreaterThan(900f)
        assertThat(state.fontSizeSp).isEqualTo(24f)
        assertThat(state.viewportHeightPx).isEqualTo(480f)
        assertThat(state.scrollOffsetPx).isEqualTo(0f)
        assertThat(state.scrollOffsetXPx).isEqualTo(0f)

        draw(renderer, state, scale = 1f)
        assertThat(renderer.performanceSnapshot().lastVisibleLineCount).isEqualTo(originalLines)
    }

    private fun draw(renderer: EditorRenderEngine, state: EditorState, scale: Float): List<Float> {
        val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        val canvas = ClipRecordingCanvas(bitmap)
        val paint = Paint().apply { typeface = Typeface.MONOSPACE; textSize = state.fontSizeSp }
        try {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(320f, 480f)) {
                drawEditorScalePreview(renderer, state, paint, Paint(paint), scale, Offset(160f, 240f))
            }
            return canvas.textBaselines
        } finally {
            bitmap.recycle()
        }
    }

    private class ClipRecordingCanvas(bitmap: Bitmap) : android.graphics.Canvas(bitmap) {
        val clips = mutableListOf<RectF>()
        val textBaselines = mutableListOf<Float>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            recordText(text, y)
            super.drawText(text, x, y, paint)
        }

        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            recordText(text.substring(start, end), y)
            super.drawText(text, start, end, x, y, paint)
        }

        private fun recordText(text: String, y: Float) {
            if (text.contains("content") || text.startsWith("line_")) textBaselines += y
        }

        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
            clips += RectF(left, top, right, bottom)
            return super.clipRect(left, top, right, bottom)
        }

        @Suppress("DEPRECATION")
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, op: Region.Op): Boolean {
            clips += RectF(left, top, right, bottom)
            return super.clipRect(left, top, right, bottom, op)
        }
    }
}
