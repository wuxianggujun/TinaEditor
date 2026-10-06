package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
class EditorScaleGestureLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val buffer = RopeTextBuffer(List(200) { "abcdefghij".repeat(20) }.joinToString("\n"))
    private val commits = mutableListOf<Float>()
    private val state = EditorState(
        buffer,
        config = EditorConfig(fontSizeSp = 16f, wordWrap = true, codeFolding = false),
        runtimeOptions = EditorRuntimeOptions(onFontSizeChanged = { commits += it })
    )
    private lateinit var session: TinaEditorSession

    @After
    fun tearDown() {
        buffer.close()
    }

    @Test
    fun transformThatStartsAndEndsBetweenCompositions_commitsBeforeReturning() {
        setUpSession()
        composeRule.runOnIdle {
            runBlocking {
                session.transformableState.transform {
                    transformBy(zoomChange = 1.125f)
                }
            }
            // No recomposition or draw is allowed between the last delta and these checks.
            assertSettled()
        }
    }

    @Test
    fun cancelledTransform_settlesTheLastPreviewAndReleasesTheWrapFreeze() {
        setUpSession()
        composeRule.runOnIdle {
            val interruption = CancellationException("Interrupted pinch")
            try {
                runBlocking {
                    session.transformableState.transform {
                        transformBy(zoomChange = 1.125f)
                        throw interruption
                    }
                }
                throw AssertionError("Transform cancellation was swallowed")
            } catch (actual: CancellationException) {
                // Coroutine stack-trace recovery may copy the exception across the suspend boundary.
                assertThat(actual).hasMessageThat().isEqualTo(interruption.message)
            }
            assertSettled()
        }
    }

    @Test
    fun nativeFontMetrics_matchTheLastPreviewAndRemainStableOnTheNextMeasurement() {
        setUpSession()
        composeRule.runOnIdle {
            state.config = state.config.copy(wordWrap = false)
            val paintMemo = PaintApplyMemo()
            for (zoom in listOf(0.75f, 1.004f, 1.237f)) {
                var previewLineTops = emptyMap<Int, Float>()
                runBlocking {
                    session.transformableState.transform {
                        transformBy(zoomChange = zoom)
                        val scale = session.ui.scaleGestureVisualScale
                        val preview = EditorRenderViewport.forScalePreview(
                            state,
                            Size(session.ui.canvasWidthPx, session.ui.canvasHeightPx),
                            scale,
                            checkNotNull(session.ui.transformGestureFocus)
                        )
                        previewLineTops = preview.visibleLines.associateWith { row ->
                            (row * state.lineHeightPx - preview.scrollOffsetPx) * scale
                        }
                    }
                }
                for ((row, expectedY) in previewLineTops) {
                    assertThat(row * state.lineHeightPx - state.scrollOffsetPx).isWithin(0.01f).of(expectedY)
                }
                val committed = state.observableState.value
                paintMemo.apply(
                    session.textPaint, session.lineNumberPaint, state.typeface,
                    with(session.density) { state.fontSizeSp.sp.toPx() }, 0
                )
                val contentStartX = session.renderer.contentStartX(state, session.lineNumberPaint)
                state.updateMetrics(
                    paintMemo.lineHeightPx, paintMemo.charWidthPx, state.viewportHeightPx,
                    session.ui.canvasWidthPx - contentStartX, contentStartX
                )
                assertThat(state.observableState.value).isEqualTo(committed)
            }
        }
    }

    private fun setUpSession() {
        composeRule.setContent {
            session = rememberTinaEditorSession(state)
        }
        composeRule.runOnIdle {
            session.ui.canvasWidthPx = 400f
            session.ui.canvasHeightPx = 600f
            session.ui.transformGestureFocus = Offset(220f, 280f)
            val fontPx = with(session.density) { state.fontSizeSp.sp.toPx() }
            session.textPaint.typeface = state.typeface
            session.lineNumberPaint.typeface = state.typeface
            session.textPaint.textSize = fontPx
            session.lineNumberPaint.textSize = fontPx
            val metrics = session.textPaint.fontMetrics
            val contentStartX = session.renderer.contentStartX(state, session.lineNumberPaint)
            state.updateMetrics(
                lineHeightPx = metrics.descent - metrics.ascent + metrics.leading,
                charWidthPx = session.textPaint.measureText("0"),
                viewportHeightPx = 600f,
                viewportWidthPx = 400f - contentStartX,
                contentStartXPx = contentStartX
            )
            state.scrollOffsetPx = 1000f
        }
    }

    private fun assertSettled() {
        assertThat(state.fontSizeSp).isEqualTo(18f)
        assertThat(session.ui.scaleGestureVisualScale).isEqualTo(1f)
        assertThat(state.isWordWrapLayoutFrozen()).isFalse()
        assertThat(commits).containsExactly(18f)
    }
}
