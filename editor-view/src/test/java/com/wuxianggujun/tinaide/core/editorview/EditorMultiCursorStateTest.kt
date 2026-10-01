package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EditorMultiCursorStateTest {

    @Test
    fun insert_shouldEditAllCursorsAndUndoAsOneOperation() {
        val state = EditorState(RopeTextBuffer("one two"))
        state.moveCursorTo(0)
        assertThat(state.addCursorAt(4)).isTrue()

        state.insert("X")

        assertThat(state.textBuffer.toString()).isEqualTo("Xone Xtwo")
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(6, 6)
        ).inOrder()
        assertThat(state.undo()).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("one two")
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(0, 0),
            OffsetRange(4, 4)
        ).inOrder()
        assertThat(state.redo()).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("Xone Xtwo")
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(6, 6)
        ).inOrder()
    }

    @Test
    fun backspace_shouldDeleteAtEveryCursor() {
        val state = EditorState(RopeTextBuffer("ab cd"))
        state.moveCursorTo(2)
        assertThat(state.addCursorAt(5)).isTrue()

        state.backspace()

        assertThat(state.textBuffer.toString()).isEqualTo("a c")
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(3, 3)
        ).inOrder()
    }

    @Test
    fun addCursorVertically_shouldKeepPrimaryCursorAndChooseNextLine() {
        val state = EditorState(RopeTextBuffer("one\ntwo\nthree"))
        state.moveCursorTo(state.textBuffer.positionToOffset(1, 1))

        assertThat(state.addCursorVertically(-1)).isTrue()
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(5, 5)
        ).inOrder()
        assertThat(state.selectionSet.primary).isEqualTo(OffsetRange(5, 5))

        assertThat(state.addCursorVertically(1)).isTrue()
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(5, 5),
            OffsetRange(9, 9)
        ).inOrder()
    }

    @Test
    fun ordinaryCursorMove_shouldExitMultiCursorMode() {
        val state = EditorState(RopeTextBuffer("abc"))
        state.moveCursorTo(0)
        assertThat(state.addCursorAt(2)).isTrue()

        state.moveCursorTo(1)

        assertThat(state.hasMultipleSelections).isFalse()
        assertThat(state.selectionSet.primary).isEqualTo(OffsetRange(1, 1))
    }

    @Test
    fun selectedText_shouldJoinDocumentOrderAndMergeOverlappingSelections() {
        val state = EditorState(RopeTextBuffer("zero one two"))
        state.applySelectionSet(
            selectionSet = EditorSelectionSet.of(
                primary = OffsetRange(9, 12),
                secondary = listOf(
                    OffsetRange(0, 4),
                    OffsetRange(2, 6)
                )
            ),
            ensureVisible = false
        )

        assertThat(state.selectedText()).isEqualTo("zero o\ntwo")
    }

    @Test
    fun selectedText_shouldIncludeSecondarySelectionWhenPrimaryIsCollapsed() {
        val state = EditorState(RopeTextBuffer("abcd"))
        state.applySelectionSet(
            selectionSet = EditorSelectionSet.of(
                primary = OffsetRange(0, 0),
                secondary = listOf(OffsetRange(2, 4))
            ),
            ensureVisible = false
        )

        assertThat(state.selectedText()).isEqualTo("cd")
    }
}
