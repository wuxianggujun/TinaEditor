package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextChange
import com.wuxianggujun.tinaide.core.treesitter.SyntaxHighlighter
import java.util.concurrent.Executor
import org.junit.Test

class EditorDocumentLifecycleTest {
    @Test
    fun closeDetachesTheBufferAndHighlighterSubscriptions() {
        RopeTextBuffer("original").use { buffer ->
            val state = EditorState(buffer)
            val highlighter = RecordingHighlighter()
            state.highlighter = highlighter
            val snapshot = state.observableState.value

            state.close()
            state.close()
            buffer.insert(0, "changed")

            assertThat(state.observableState.value).isEqualTo(snapshot)
            assertThat(highlighter.changes).isEmpty()
            assertThat(highlighter.callback).isNull()
            assertThat(buffer.isClosed).isFalse()
        }
    }

    @Test
    fun queuedChangesCannotReviveAClosedDocument() {
        val tasks = ArrayDeque<Runnable>()
        RopeTextBuffer("original", changeExecutor = Executor { tasks.addLast(it) }).use { buffer ->
            val state = EditorState(buffer)
            val highlighter = RecordingHighlighter()
            state.highlighter = highlighter
            val snapshot = state.observableState.value
            buffer.insert(0, "first")
            buffer.insert(0, "second")

            state.close()
            while (tasks.isNotEmpty()) tasks.removeFirst().run()

            assertThat(highlighter.changes).isEmpty()
            assertThat(state.observableState.value).isEqualTo(snapshot)
        }
    }

    @Test
    fun replacingAHighlighterDetachesItsPreviousCallback() {
        RopeTextBuffer("original").use { buffer ->
            val state = EditorState(buffer)
            val previous = RecordingHighlighter()
            val current = RecordingHighlighter()
            state.highlighter = previous
            state.highlighter = current
            buffer.insert(0, "changed")

            assertThat(previous.callback).isNull()
            assertThat(previous.changes).isEmpty()
            assertThat(current.callback).isNotNull()
            assertThat(current.changes).hasSize(1)
            state.close()
        }
    }

    @Test
    fun directBufferEditsUpdateStateWithoutHostOrViewSubscriptions() {
        RopeTextBuffer("original").use { buffer ->
            val state = EditorState(buffer)

            buffer.insert(0, "中文🙂\n")

            assertThat(state.observableState.value.documentVersion).isEqualTo(buffer.version)
            assertThat(state.observableState.value.documentLength).isEqualTo(buffer.length)
        }
    }

    @Test
    fun editsAndUndoReachTheInjectedHighlighterExactlyOnce() {
        RopeTextBuffer("val number = 42").use { buffer ->
            val state = EditorState(buffer)
            val highlighter = RecordingHighlighter()
            state.highlighter = highlighter

            state.replaceRange(13, 15, "\"中文🙂\"")
            assertThat(state.undo()).isTrue()
            assertThat(state.redo()).isTrue()

            assertThat(highlighter.changes.map { it.documentVersion }).containsExactly(1L, 2L, 3L).inOrder()
            assertThat(highlighter.changes.map { it.newText }).containsExactly("\"中文🙂\"", "42", "\"中文🙂\"").inOrder()
        }
    }

    private class RecordingHighlighter : SyntaxHighlighter {
        val changes = mutableListOf<TextChange>()
        var callback: (() -> Unit)? = null

        override fun openDocumentBlocking(text: String) = Unit
        override fun applyTextChange(change: TextChange) { changes += change }
        override fun setOnStateUpdated(callback: (() -> Unit)?) { this.callback = callback }
    }
}
