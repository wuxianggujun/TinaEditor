package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorLifecycleInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closingBeforeSnapshotApply_doesNotReadReleasedBufferOnDevice() {
        val buffer = RopeTextBuffer(List(80) { "line $it" }.joinToString("\n"))
        val state = EditorState(buffer).apply { updateMetrics(20f, 10f, 200f, 320f, 0f) }
        val observer = SnapshotStateObserver { it() }
        composeRule.runOnUiThread {
            observer.start()
            try {
                Snapshot.sendApplyNotifications()
                observer.observeReads(Any(), {}) { assertEquals(0..12, state.visibleLines) }
                state.viewportHeightPx = 240f
                buffer.close()
                Snapshot.sendApplyNotifications()
                assertTrue(state.visibleLines.isEmpty())
                assertTrue(state.visibleDocumentLines.isEmpty())
            } finally {
                observer.stop()
                observer.clear()
                buffer.close()
            }
        }
    }

    @Test
    fun pinchRelease_keepsDocumentAnchorAndViewportOnDevice() {
        val buffer = RopeTextBuffer(List(200) { "int value_$it = 123;" }.joinToString("\n"))
        val publishedSizes = mutableListOf<Float>()
        val state = EditorState(
            buffer,
            config = EditorConfig(wordWrap = false),
            runtimeOptions = EditorRuntimeOptions(onFontSizeChanged = { publishedSizes += it })
        )
        var mounted by mutableStateOf(true)
        try {
            composeRule.setContent {
                if (mounted) TinaEditor(state, Modifier.fillMaxSize().testTag("pinch-editor"))
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue(state.viewportHeightPx > 1f)
                state.scrollToLine(60)
            }
            var focusY = 0f
            var anchorBefore = 0f
            val initialSize = state.fontSizeSp
            composeRule.onNodeWithTag("pinch-editor").performTouchInput {
                focusY = center.y
                anchorBefore = (state.scrollOffsetPx + focusY) / state.lineHeightPx
                pinch(
                    start0 = center - Offset(width * 0.15f, 0f),
                    end0 = center - Offset(width * 0.3f, 0f),
                    start1 = center + Offset(width * 0.15f, 0f),
                    end1 = center + Offset(width * 0.3f, 0f),
                    durationMillis = 320L
                )
            }
            composeRule.waitForIdle()
            val committedScroll = state.scrollOffsetPx
            composeRule.runOnIdle {
                assertEquals(1, publishedSizes.size)
                assertTrue(state.fontSizeSp > initialSize)
                assertEquals(anchorBefore, (state.scrollOffsetPx + focusY) / state.lineHeightPx, 0.1f)
            }
            repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
            composeRule.runOnIdle {
                assertEquals(committedScroll, state.scrollOffsetPx, 0.1f)
                assertEquals(publishedSizes.single(), state.fontSizeSp)
            }
            // RuntimeEffects intentionally keeps the multi-touch guard for 100 ms
            // after transform completion. Let that coroutine settle before a new drag.
            composeRule.mainClock.advanceTimeBy(200L)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("pinch-editor").performTouchInput {
                // The bottom edge belongs to the horizontal scrollbar in no-wrap mode.
                // Start inside the document, not on the scrollbar's drag hit target.
                swipeUp(startY = height * 0.75f, endY = height * 0.25f, durationMillis = 240L)
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue(
                    "Single-finger scrolling must still work: before=$committedScroll " +
                        "after=${state.scrollOffsetPx} max=${state.maxVerticalScrollOffsetPx()}",
                    state.scrollOffsetPx > committedScroll
                )
                assertEquals(1, publishedSizes.size)
            }
        } finally {
            // Leave composition before releasing the resource owned by this fixture.
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
            buffer.close()
        }
    }
}
