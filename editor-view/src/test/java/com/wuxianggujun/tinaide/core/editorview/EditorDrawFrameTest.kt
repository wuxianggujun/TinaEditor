package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextBufferClosedException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorDrawFrameTest {
    private val buffer = RopeTextBuffer("content")
    private val state = EditorState(buffer)
    private val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

    @After
    fun tearDown() {
        buffer.close()
        bitmap.recycle()
    }

    @Test
    fun openBufferDrawsNormally() {
        drawFrame {
            assertThat(buffer.lineCount).isEqualTo(1)
            drawRect(Color.Red)
        }
        assertPixels(Color.Red)
    }

    @Test
    fun closedBufferSkipsAllContentAndDrawsBackground() {
        bitmap.eraseColor(Color.Red.toArgb())
        buffer.close()

        drawFrame { error("Closed frames must not call the renderer") }

        assertPixels(state.colorScheme.background)
    }

    @Test
    fun closeOnAnotherThreadDuringFrameClearsPartialContentAndRestoresClip() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            drawFrame {
                drawRect(Color.Red)
                // The frame already passed isClosed. Force close to win before the
                // next read, deterministically testing the check/read race without sleeps.
                clipRect(right = 8f, bottom = 8f) {
                    executor.submit { buffer.close() }.get(5, TimeUnit.SECONDS)
                    buffer.lineCount
                    error("The closed-buffer read must throw")
                }
            }
            assertPixels(state.colorScheme.background)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun unrelatedStateErrorsAreNotSwallowedEvenAfterClose() {
        val failure = IllegalStateException("renderer invariant failed")
        val actual = assertThrows(IllegalStateException::class.java) {
            drawFrame {
                buffer.close()
                throw failure
            }
        }
        assertThat(actual).isSameInstanceAs(failure)
    }

    @Test
    fun closedExceptionFromOtherBufferIsNotSwallowed() {
        val failure = TextBufferClosedException("another buffer")
        val actual = assertThrows(TextBufferClosedException::class.java) {
            drawFrame { throw failure }
        }
        assertThat(actual).isSameInstanceAs(failure)
        assertThat(buffer.isClosed).isFalse()
    }

    private fun drawFrame(content: DrawScope.() -> Unit) {
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(16f, 16f)) {
            drawEditorFrame(state, content)
        }
    }

    private fun assertPixels(color: Color) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertThat(bitmap.getPixel(x, y)).isEqualTo(color.toArgb())
            }
        }
    }
}
