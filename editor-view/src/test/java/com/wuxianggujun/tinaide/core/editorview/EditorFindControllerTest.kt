package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorFindControllerTest {
    private val buffers = mutableListOf<RopeTextBuffer>()

    @After fun tearDown() { buffers.forEach { it.close() } }

    @Test fun openingAndClosingFind_keepsReverseUnicodeAndMultipleSelections() {
        val state = state("中文😀abcdefghij\r\nnext line")
        val original = EditorSelectionSet.of(OffsetRange(14, 4), listOf(OffsetRange(0, 2)))
        state.applySelectionSet(original, ensureVisible = false)
        state.find.show()
        state.find.refresh()
        assertThat(state.selectionSet).isEqualTo(original)
        state.find.dismiss()
        assertThat(state.selectionSet).isEqualTo(original)
    }

    @Test fun closingFind_restoresOriginalSelectionAfterQueryChangesAndNavigation() {
        val state = state("abcdefghij abc abc")
        val original = EditorSelectionSet.of(OffsetRange(10, 0), listOf(OffsetRange(14, 17)))
        state.applySelectionSet(original, ensureVisible = false)
        state.find.show()
        state.find.updateQuery("abc")
        state.find.refresh()
        state.find.next()
        assertThat(state.selectionSet).isNotEqualTo(original)
        // Ctrl+H within the same session must not capture the temporary match as the origin.
        state.find.show(replace = true)
        assertThat(state.find.query).isEqualTo("abc")
        state.find.dismiss()
        assertThat(state.selectionSet).isEqualTo(original)
    }

    @Test fun rememberedRegex_doesNotShrinkTheSelectionWhenOpeningFind() {
        val state = state("abc(def)gh abcdefgh")
        state.selectRange(0, 10)
        val original = state.selectionSet
        state.find.updateOptions(EditorFindOptions(regex = true))
        state.find.show()
        state.find.refresh()
        assertThat(state.find.matchCount).isEqualTo(1)
        assertThat(state.selectedText()).isEqualTo("abc(def)gh")
        state.find.next()
        assertThat(state.selectedText()).isEqualTo("abcdefgh")
        state.find.dismiss()
        assertThat(state.selectionSet).isEqualTo(original)
    }

    @Test fun findOpenedWithoutSelection_keepsTheNavigatedMatchOnClose() {
        val state = state("prefix abc abc")
        state.find.updateQuery("abc")
        state.find.show()
        state.find.refresh()
        assertThat(state.selectionRange).isNull()
        state.find.next()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(7, 10))
        state.find.dismiss()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(7, 10))
    }

    @Test fun navigatingBeforeDebouncedRefresh_doesNotSkipTheFirstMatch() {
        val state = state("foo foo")
        state.find.show()
        state.find.updateQuery("foo")
        state.find.next()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(0, 3))
        state.find.next()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(4, 7))
        state.find.updateQuery("oo")
        state.find.next()
        assertThat(state.selectionRange).isEqualTo(OffsetRange(5, 7))
    }

    @Test fun documentClosedDuringSearch_discardsResultsAndMakesPanelActionsNoOps() = runTest {
        val state = state("foo foo")
        state.find.show()
        state.find.updateQuery("foo")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { state.find.refreshAsync() }
        state.textBuffer.close()
        pending.await()
        state.find.dismiss()
        state.find.show()
        assertThat(state.find.visible).isFalse()
        assertThat(state.find.matches).isEmpty()
        assertThat(state.find.next()).isFalse()
        assertThat(state.find.replaceCurrent()).isFalse()
        assertThat(state.find.replaceAll()).isEqualTo(0)
    }

    @Test fun closingFind_doesNotRestoreStaleOffsetsAfterReplacementOrManualSelection() {
        val state = state("abcdefghij abc")
        state.selectRange(0, 10)
        state.find.show()
        state.find.updateQuery("abc")
        state.find.refresh()
        state.find.replacement = "x"
        state.find.replaceAll()
        val afterEdit = state.selectionSet
        state.find.dismiss()
        assertThat(state.selectionSet).isEqualTo(afterEdit)

        state.find.show()
        state.find.search("x")
        state.moveCursorTo(state.textBuffer.length)
        val manual = state.selectionSet
        state.find.dismiss()
        assertThat(state.selectionSet).isEqualTo(manual)
    }

    @Test fun asyncResultFromClosedSession_cannotChangeTheSelectionOrReopenedSession() = runTest {
        val state = state("abcdefghij abc")
        state.selectRange(0, 10)
        val original = state.selectionSet
        state.find.show()
        state.find.updateQuery("abc")
        // The worker result resumes on this test scheduler only after we close/reopen.
        val pending = async(start = CoroutineStart.UNDISPATCHED) { state.find.refreshAsync() }
        state.find.dismiss()
        state.find.show()
        state.find.updateQuery("abc")
        pending.await()
        assertThat(state.find.matches).isEmpty()
        assertThat(state.selectionSet).isEqualTo(original)
        state.find.dismiss()
        state.find.refreshAsync()
        assertThat(state.selectionSet).isEqualTo(original)
    }

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
        buffers += buffer
        return EditorState(buffer)
    }
}
