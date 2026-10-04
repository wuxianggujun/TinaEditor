package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextChange
import java.util.concurrent.Executor
import org.junit.Test

class EditorStateInlayHintsTest {
    @Test
    fun typingAtBottom_shouldKeepTopHintsAndTheirLayoutGeneration() {
        val buffer = RopeTextBuffer("call(1)\n\nbottom")
        val state = EditorState(buffer)
        val hint = EditorInlayHint(0, 5, "value:")
        state.replaceInlayHintsInLines(0..0, listOf(hint), buffer.version)
        val hintsByLine = state.inlayHintsByLine
        val layoutGeneration = state.inlayHintsVersion
        buffer.addChangeListener(state::applyTextBufferChange)

        repeat(4) {
            buffer.insert(buffer.length, "x")
            assertThat(state.inlayHintsByLine).isSameInstanceAs(hintsByLine)
            assertThat(state.inlayHints).containsExactly(hint)
            assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
            assertThat(state.inlayHintsVersion).isEqualTo(layoutGeneration)
        }
    }

    @Test
    fun editingHintedLine_shouldInvalidateOnlyThatLine() {
        val buffer = RopeTextBuffer("call(1)\ncall(2)\ncall(3)")
        val state = EditorState(buffer)
        val hints = List(3) { EditorInlayHint(it, 5, "value:") }
        state.replaceInlayHintsInLines(0..2, hints, buffer.version)
        buffer.addChangeListener(state::applyTextBufferChange)

        buffer.insert(buffer.positionToOffset(1, 5), "2")

        assertThat(state.inlayHints).containsExactly(hints[0], hints[2]).inOrder()
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }

    @Test
    fun newlineInsertionAndDeletion_shouldRebaseUntouchedHints() {
        val buffer = RopeTextBuffer("call(1)\nedit\ncall(2)")
        val state = EditorState(buffer)
        val top = EditorInlayHint(0, 5, "first:")
        val bottom = EditorInlayHint(2, 5, "second:")
        state.replaceInlayHintsInLines(0..2, listOf(top, bottom), buffer.version)
        buffer.addChangeListener(state::applyTextBufferChange)
        val editOffset = buffer.positionToOffset(1, 2)

        buffer.insert(editOffset, "\n\n")
        assertThat(state.inlayHints).containsExactly(top, bottom.copy(line = 4)).inOrder()
        assertThat(state.inlayHintsByLine.keys).containsExactly(0, 4)

        buffer.delete(editOffset, editOffset + 2)
        assertThat(state.inlayHints).containsExactly(top, bottom).inOrder()
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }

    @Test
    fun freshUnchangedResponse_shouldNotInvalidateLayoutAgain() {
        val buffer = RopeTextBuffer("call(1)\nbottom")
        val state = EditorState(buffer)
        val hint = EditorInlayHint(0, 5, "value:")
        state.replaceInlayHintsInLines(0..0, listOf(hint), buffer.version)
        val layoutGeneration = state.inlayHintsVersion
        buffer.addChangeListener(state::applyTextBufferChange)
        buffer.insert(buffer.length, "x")

        assertThat(state.replaceInlayHintsInLines(0..1, listOf(hint), buffer.version)).isTrue()
        assertThat(state.inlayHintsVersion).isEqualTo(layoutGeneration)
    }

    @Test
    fun staleResponse_shouldNotOverwriteRebasedHints() {
        val buffer = RopeTextBuffer("edit\ncall(1)")
        val state = EditorState(buffer)
        val hint = EditorInlayHint(1, 5, "value:")
        state.replaceInlayHintsInLines(0..1, listOf(hint), buffer.version)
        val requestedVersion = buffer.version
        buffer.addChangeListener(state::applyTextBufferChange)
        buffer.insert(2, "\n")

        assertThat(state.replaceInlayHintsInLines(0..1, listOf(hint), requestedVersion)).isFalse()
        assertThat(state.inlayHints).containsExactly(hint.copy(line = 2))
    }

    @Test
    fun delayedChangeBatch_shouldUseEachEventVersionWhenRebasing() {
        val tasks = ArrayDeque<Runnable>()
        val buffer = RopeTextBuffer("edit\ncall(1)", changeExecutor = Executor { tasks.addLast(it) })
        val state = EditorState(buffer)
        val hint = EditorInlayHint(1, 5, "value:")
        state.replaceInlayHintsInLines(0..1, listOf(hint), buffer.version)
        val observedHintVersions = mutableListOf<Long>()
        buffer.addChangeListener { change ->
            state.applyTextBufferChange(change)
            observedHintVersions += state.inlayHintsDocumentVersion
        }

        buffer.insert(2, "\n")
        buffer.insert(2, "\n")
        tasks.removeFirst().run()

        assertThat(observedHintVersions).containsExactly(1L, 2L).inOrder()
        assertThat(state.inlayHints).containsExactly(hint.copy(line = 3))
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }

    @Test
    fun multiLineReplacement_shouldDropEditedRowsAndShiftOnlyTheTail() {
        val buffer = RopeTextBuffer("top\nfirst\nsecond\ntail")
        val state = EditorState(buffer)
        val hints = List(4) { EditorInlayHint(it, 1, "hint-$it") }
        state.replaceInlayHintsInLines(0..3, hints, buffer.version)
        buffer.addChangeListener(state::applyTextBufferChange)

        buffer.replace(buffer.positionToOffset(1, 1), buffer.positionToOffset(2, 3), "x")

        assertThat(state.inlayHints).containsExactly(hints[0], hints[3].copy(line = 2)).inOrder()
        assertThat(state.inlayHintsByLine.keys).containsExactly(0, 2)
    }

    @Test
    fun missingChange_shouldDiscardHintsRatherThanGuessTheirPositions() {
        val buffer = RopeTextBuffer("edit\ncall(1)")
        val state = EditorState(buffer)
        state.replaceInlayHintsInLines(0..1, listOf(EditorInlayHint(1, 5, "value:")), buffer.version)
        val changes = mutableListOf<TextChange>()
        buffer.addChangeListener { changes += it }
        buffer.insert(2, "\n")
        buffer.insert(2, "\n")

        state.applyTextBufferChange(changes.last())

        assertThat(state.inlayHints).isEmpty()
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(-1L)
    }

    @Test
    fun currentResponseBeforeQueuedChanges_shouldNotBeRebasedTwice() {
        val tasks = ArrayDeque<Runnable>()
        val buffer = RopeTextBuffer("edit\ncall(1)", changeExecutor = Executor { tasks.addLast(it) })
        val state = EditorState(buffer)
        buffer.addChangeListener(state::applyTextBufferChange)
        buffer.insert(2, "\n")
        buffer.insert(2, "\n")
        val currentHint = EditorInlayHint(3, 5, "value:")
        state.replaceInlayHintsInLines(0..3, listOf(currentHint), buffer.version)
        val layoutGeneration = state.inlayHintsVersion

        tasks.removeFirst().run()

        assertThat(state.inlayHints).containsExactly(currentHint)
        assertThat(state.inlayHintsVersion).isEqualTo(layoutGeneration)
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }

    @Test
    fun freshEmptyResponse_shouldRemoveRetainedHintsOnlyInsideRequestedWindow() {
        val buffer = RopeTextBuffer("top\nedit\nbottom")
        val state = EditorState(buffer)
        val top = EditorInlayHint(0, 1, "top:")
        val bottom = EditorInlayHint(2, 1, "bottom:")
        state.replaceInlayHintsInLines(0..2, listOf(top, bottom), buffer.version)
        buffer.addChangeListener(state::applyTextBufferChange)
        buffer.insert(buffer.positionToOffset(1, 2), "x")

        assertThat(state.replaceInlayHintsInLines(0..1, emptyList(), buffer.version)).isTrue()
        assertThat(state.inlayHints).containsExactly(bottom)
    }

    @Test
    fun explicitlyClearedHints_shouldStayClearedWhileTyping() {
        val buffer = RopeTextBuffer("call(1)\nbottom")
        val state = EditorState(buffer)
        state.replaceInlayHintsInLines(0..0, listOf(EditorInlayHint(0, 5, "value:")), buffer.version)
        state.clearInlayHints()
        val layoutGeneration = state.inlayHintsVersion
        buffer.addChangeListener(state::applyTextBufferChange)

        buffer.insert(buffer.length, "x")

        assertThat(state.inlayHints).isEmpty()
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(-1L)
        assertThat(state.inlayHintsVersion).isEqualTo(layoutGeneration)
    }

    @Test
    fun replaceInlayHintsInLines_shouldMergeViewportAtCurrentVersion() {
        val buffer = RopeTextBuffer("call(1)\nvalue")
        val state = EditorState(buffer)
        val parameterHint = EditorInlayHint(
            line = 0,
            column = 5,
            label = "value:",
            kind = EditorInlayHintKind.PARAMETER,
            paddingRight = true,
        )
        val typeHint = EditorInlayHint(
            line = 1,
            column = 5,
            label = ": int",
            kind = EditorInlayHintKind.TYPE,
        )

        assertThat(state.replaceInlayHintsInLines(0..0, listOf(parameterHint), buffer.version)).isTrue()
        assertThat(state.replaceInlayHintsInLines(1..1, listOf(typeHint), buffer.version)).isTrue()

        assertThat(state.inlayHints).containsExactly(parameterHint, typeHint).inOrder()
        assertThat(state.inlayHintsByLine.keys).containsExactly(0, 1)
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }

    @Test
    fun replaceInlayHintsInLines_shouldRejectStaleVersion() {
        val buffer = RopeTextBuffer("value")
        val state = EditorState(buffer)
        buffer.insert(0, "x")

        val applied = state.replaceInlayHintsInLines(
            lines = 0..0,
            hints = listOf(EditorInlayHint(0, 1, ": int")),
            documentVersion = buffer.version - 1,
        )

        assertThat(applied).isFalse()
        assertThat(state.inlayHints).isEmpty()
    }

    @Test
    fun applyTextBufferChange_shouldDiscardHintsOnEditedLine() {
        val buffer = RopeTextBuffer("call(1)")
        val state = EditorState(buffer)
        state.replaceInlayHintsInLines(
            lines = 0..0,
            hints = listOf(EditorInlayHint(0, 5, "value:", EditorInlayHintKind.PARAMETER)),
            documentVersion = buffer.version,
        )
        val change = TextChange(
            startOffset = 5,
            endOffset = 5,
            oldText = "",
            newText = "2",
            startLine = 0,
            startColumn = 5,
            endLine = 0,
            endColumn = 5,
        )
        buffer.insert(5, "2")

        state.applyTextBufferChange(change)

        assertThat(state.inlayHints).isEmpty()
        assertThat(state.inlayHintsByLine).isEmpty()
        assertThat(state.inlayHintsDocumentVersion).isEqualTo(buffer.version)
    }
}
