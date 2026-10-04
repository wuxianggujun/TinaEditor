package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.Position
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorManualMultiCursorTest {
    @Test fun togglePromotesAnotherCursorButNeverRemovesTheLastCursor() {
        val state = state("abcd")
        assertThat(state.toggleCursorAt(3)).isTrue()
        assertThat(state.removeCursorAt(0, 0)).isTrue()
        assertThat(state.cursorOffset).isEqualTo(3)
        assertThat(state.toggleCursorAt(3)).isFalse()
        assertThat(state.selectionSet.selections).hasSize(1)
    }

    @Test fun logicalRectangleClampsShortLinesAndKeepsDirection() {
        val state = state("abcd\nx\nabcdef")
        state.selectRectangle(Position(2, 4), Position(0, 1))
        assertThat(state.selectionSet.selections).containsExactly(
            OffsetRange(4, 1), OffsetRange(6, 6), OffsetRange(11, 8)).inOrder()
        assertThat(state.selectionSet.primary).isEqualTo(OffsetRange(4, 1))
        state.replaceSelection("z")
        assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("az\nxz\nazef")
    }

    @Test fun addingByPositionDoesNotSplitSurrogatePair() {
        val state = state("a😀b")
        state.addCursorAt(0, 2)
        assertThat(state.selectionSet.secondary.single().caret).isEqualTo(3)
    }

    private fun state(text: String) = EditorState(RopeTextBuffer().apply { insert(0, text) })
}
