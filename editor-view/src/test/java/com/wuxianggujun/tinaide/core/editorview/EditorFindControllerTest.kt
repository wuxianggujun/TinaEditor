package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextChangeListener
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorFindControllerTest {
    @Test fun navigationWrapsAndOptionsInvalidateOldMatchesImmediately() {
        val state = state("foo Foo foo")
        state.find.search("foo")
        assertThat(state.find.matchCount).isEqualTo(3)
        state.find.previous()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(8, 11))
        state.find.next()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(0, 3))
        state.find.updateOptions(EditorFindOptions(caseSensitive = true))
        assertThat(state.find.matches).isEmpty()
        state.find.refresh()
        assertThat(state.find.matchCount).isEqualTo(2)
    }

    @Test fun replaceAllIsOneUndoAndKeepsUnrelatedSelections() {
        val state = state("foo foo end")
        state.moveCursorTo(11)
        state.find.search("foo")
        state.moveCursorTo(11)
        state.find.replacement = "x"
        assertThat(state.find.replaceAll()).isEqualTo(2)
        assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("x x end")
        assertThat(state.cursorOffset).isEqualTo(7)
        state.undo()
        assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("foo foo end")
        assertThat(state.cursorOffset).isEqualTo(11)
    }

    @Test fun textChangesInvalidateResultsWithoutMovingTheTypingCursor() {
        val state = state("foo end")
        state.find.search("foo")
        state.moveCursorTo(7)
        state.insertUserInput(" foo")
        val cursor = state.cursorOffset
        assertThat(state.find.matches).isEmpty()
        state.find.refresh()
        assertThat(state.find.matchCount).isEqualTo(2)
        assertThat(state.cursorOffset).isEqualTo(cursor)
    }

    @Test fun replaceCurrentAdvancesPastInsertedMatchAndInvalidGroupsDoNotEdit() {
        val state = state("a a")
        state.find.search("a", EditorFindOptions(regex = true))
        state.find.replacement = "aa"
        assertThat(state.find.replaceCurrent()).isTrue()
        assertThat(state.find.activeIndex).isEqualTo(2)
        val version = state.textBuffer.version
        state.find.replacement = "$5"
        assertThat(state.find.replaceAll()).isEqualTo(0)
        assertThat(state.textBuffer.version).isEqualTo(version)
        assertThat(state.find.error).isEqualTo(EditorFindError.InvalidReplacement)
    }

    private fun state(text: String): EditorState {
        val buffer = RopeTextBuffer().apply { insert(0, text) }
        return EditorState(buffer).also { state ->
            buffer.addChangeListener(TextChangeListener(state::applyTextBufferChange))
        }
    }
}
