package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextBufferClosedException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Test

class EditorClosedSnapshotTest {
    @Test
    fun closeWithoutSnapshotChange_shouldNotReuseCachedVisibleLines() {
        val buffer = RopeTextBuffer("first\nsecond")
        val state = EditorState(buffer)
        assertThat(state.visibleLines.isEmpty()).isFalse()
        buffer.close()
        assertThat(state.visibleLines.isEmpty()).isTrue()
        assertThat(state.visibleDocumentLines.isEmpty()).isTrue()
        assertThrows(TextBufferClosedException::class.java) { buffer.lineCount }
    }

    @Test
    fun closingBetweenCheckAndMapping_shouldRetireTheVisibleRange() {
        val buffer = RopeTextBuffer("text")
        val executor = Executors.newSingleThreadExecutor()
        val wrapper = object : TextBuffer by buffer {
            override val lineCount: Int
                get() {
                    executor.submit { buffer.close() }.get(5, TimeUnit.SECONDS)
                    return buffer.lineCount
                }
        }
        try {
            val state = EditorState(wrapper)
            assertThat(state.visibleLines.isEmpty()).isTrue()
            assertThat(state.visibleDocumentLines.isEmpty()).isTrue()
        } finally {
            executor.shutdownNow()
            buffer.close()
        }
    }

    @Test
    fun unrelatedMappingFailure_shouldStillPropagate() {
        verifyMappingFailure(IllegalStateException("invalid mapping"), closeBuffer = true)
    }

    @Test
    fun closedExceptionWithOpenBuffer_shouldStillPropagate() {
        verifyMappingFailure(TextBufferClosedException("other buffer"), closeBuffer = false)
    }

    private fun verifyMappingFailure(failure: IllegalStateException, closeBuffer: Boolean) {
        val buffer = RopeTextBuffer("text")
        val wrapper = object : TextBuffer by buffer {
            override val lineCount: Int
                get() {
                    if (closeBuffer) buffer.close()
                    throw failure
                }
        }
        try {
            val state = EditorState(wrapper)
            val thrown = assertThrows(IllegalStateException::class.java) { state.visibleLines }
            assertThat(thrown).isSameInstanceAs(failure)
        } finally {
            buffer.close()
        }
    }

    @Test
    fun closedBuffer_shouldNotCrashDerivedStateInvalidationBeforeObserverDisposal() {
        val buffer = RopeTextBuffer(List(80) { "line $it" }.joinToString("\n"))
        val state = EditorState(buffer).apply {
            updateMetrics(20f, 10f, 200f, 320f, 0f)
        }
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            Snapshot.sendApplyNotifications()
            observer.observeReads(Any(), {}) {
                assertThat(state.visibleLines).isEqualTo(0..12)
            }
            // Keep the UI observer registered across runtime disposal, as in save-and-close.
            state.viewportHeightPx = 240f
            buffer.close()
            Snapshot.sendApplyNotifications()
            assertThat(state.visibleLines.isEmpty()).isTrue()
            assertThat(state.visibleDocumentLines.isEmpty()).isTrue()
        } finally {
            observer.stop()
            observer.clear()
            buffer.close()
        }
    }
}
