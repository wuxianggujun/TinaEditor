package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test

class EditorMultiCursorLineCommentTest {

    @Test
    fun toggleLineComment_shouldCommentEveryMultiCursorLineOnce() {
        val state = createState("one\n  two\n\tthree\nfour")
        state.applySelectionSet(
            selectionSet = EditorSelectionSet.of(
                primary = OffsetRange(1, 1),
                secondary = listOf(OffsetRange(7, 7), OffsetRange(15, 15))
            ),
            ensureVisible = false
        )

        assertThat(state.toggleLineComment("//")).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo(
            "// one\n  // two\n\t// three\nfour"
        )
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(4, 4),
            OffsetRange(13, 13),
            OffsetRange(24, 24)
        ).inOrder()
    }

    @Test
    fun toggleLineComment_shouldPreserveCrlfAndRestoreAllSelectionsThroughUndoRedo() {
        val state = createState("// one\r\ntwo\r\n// three")
        val originalSelections = EditorSelectionSet.of(
            primary = OffsetRange(anchor = 5, caret = 5),
            secondary = listOf(OffsetRange(anchor = 20, caret = 14))
        )
        state.applySelectionSet(originalSelections, ensureVisible = false)

        assertThat(state.toggleLineComment("//")).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("one\r\ntwo\r\nthree")
        assertThat(state.selectionSet).isEqualTo(
            EditorSelectionSet.of(
                primary = OffsetRange(anchor = 2, caret = 2),
                secondary = listOf(OffsetRange(anchor = 14, caret = 10))
            )
        )

        assertThat(state.undo()).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("// one\r\ntwo\r\n// three")
        assertThat(state.selectionSet).isEqualTo(originalSelections)

        assertThat(state.redo()).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("one\r\ntwo\r\nthree")
        assertThat(state.selectionSet).isEqualTo(
            EditorSelectionSet.of(
                primary = OffsetRange(anchor = 2, caret = 2),
                secondary = listOf(OffsetRange(anchor = 14, caret = 10))
            )
        )
    }

    @Test
    fun toggleLineComment_shouldNotDoubleCommentMixedLines() {
        val state = createState("// one\ntwo\n// three")
        state.applySelectionSet(
            selectionSet = EditorSelectionSet.of(
                primary = OffsetRange(1, 1),
                secondary = listOf(OffsetRange(9, 9), OffsetRange(15, 15))
            ),
            ensureVisible = false
        )

        assertThat(state.toggleLineComment("//")).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("// one\n// two\n// three")
    }

    @Test
    fun toggleLineComment_shouldMergeAdjacentSelectionsAndUncommentTogether() {
        val state = createState("// one\n// two\nthree")
        state.applySelectionSet(
            selectionSet = EditorSelectionSet.of(
                primary = OffsetRange(1, 1),
                secondary = listOf(OffsetRange(10, 10))
            ),
            ensureVisible = false
        )

        assertThat(state.toggleLineComment("//")).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("one\ntwo\nthree")
    }

    private fun createState(text: String): EditorState =
        EditorState(RopeTextBuffer(text))
}
