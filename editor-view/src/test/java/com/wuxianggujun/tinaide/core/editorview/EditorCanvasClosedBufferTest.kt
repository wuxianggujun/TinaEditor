package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorCanvasClosedBufferTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val buffer = RopeTextBuffer(List(200) { "line $it" }.joinToString("\n"))
    private val state = EditorState(buffer, config = EditorConfig(showMinimap = true)).apply { isFocused = true }
    private lateinit var session: TinaEditorSession
    private lateinit var hostView: View

    @After
    fun tearDown() {
        buffer.close()
    }

    @Test
    fun closedBuffer_shouldNotCrashNextContentAndCursorFrame() = verifyClosedFrame(scale = 1f)

    @Test
    fun closedBuffer_shouldNotCrashZoomPreviewOrMinimap() = verifyClosedFrame(scale = 0.5f)

    private fun verifyClosedFrame(scale: Float) {
        composeRule.setContent {
            hostView = LocalView.current
            session = rememberTinaEditorSession(state)
            EditorCanvasLayer(session)
        }
        composeRule.waitForIdle()
        drawHostView()
        val renderedFrames = session.renderer.performanceSnapshot().totalRenderedFrames
        assertThat(renderedFrames).isGreaterThan(0L)
        assertThat(session.minimapRenderer.calculateLayout(
            state, session.ui.canvasWidthPx, session.ui.canvasHeightPx, session.density
        )).isNotNull()

        // Keep the old Canvas nodes attached and invalidate both draw layers after
        // close, reproducing the stale-frame window without relying on disposal timing.
        composeRule.runOnIdle {
            buffer.close()
            session.ui.scaleGestureVisualScale = scale
            state.notifyHighlightChanged()
            state.cursorBlinkVisible = !state.cursorBlinkVisible
        }
        composeRule.waitForIdle()
        drawHostView()

        assertThat(session.renderer.performanceSnapshot().totalRenderedFrames).isEqualTo(renderedFrames)
    }

    private fun drawHostView() {
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            try {
                hostView.draw(Canvas(bitmap))
            } finally {
                bitmap.recycle()
            }
        }
    }
}
