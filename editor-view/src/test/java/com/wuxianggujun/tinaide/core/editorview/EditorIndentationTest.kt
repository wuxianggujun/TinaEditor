package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test

class EditorIndentationTest {
    @Test
    fun legacyConfiguration_inheritsTabWidthForIndentation() {
        assertThat(EditorConfig(tabSize = 8).indentSize).isEqualTo(8)
    }

    @Test
    fun newline_usesIndentWidthInsteadOfTabDisplayWidth() {
        val state = state("  if (ready) {", EditorConfig(indentSize = 2, tabSize = 8))
        state.insertUserInput("\n")
        assertThat(state.textBuffer.toString()).isEqualTo("  if (ready) {\n    ")
    }

    @Test
    fun newline_onOrdinaryLinePreservesExistingIndentation() {
        val state = state("  work();", EditorConfig(indentSize = 4, tabSize = 8))
        state.insertUserInput("\n")
        assertThat(state.textBuffer.toString()).isEqualTo("  work();\n  ")
    }

    @Test
    fun tabs_smallerIndentWidthDoesNotInsertAnOversizedTab() {
        val config = EditorConfig(indentSize = 2, tabSize = 8, insertSpacesForTabs = false)
        val state = state("", config)
        state.insertUserInput("\t")
        assertThat(state.textBuffer.toString()).isEqualTo("  ")
        assertThat(buildEditorIndentation(8, config)).isEqualTo("\t")
        assertThat(buildEditorIndentation(10, config)).isEqualTo("\t  ")
    }

    @Test
    fun tab_inCodeUsesTabStopsButLeadingTabUsesIndentationStops() {
        val config = EditorConfig(indentSize = 2, tabSize = 8)
        val leading = state("", config)
        leading.insertUserInput("\t")
        assertThat(leading.textBuffer.toString()).isEqualTo("  ")
        val code = state("x", config)
        code.insertUserInput("\t")
        assertThat(code.textBuffer.toString()).isEqualTo("x       ")
    }

    @Test
    fun outdent_removesOneIndentLevelNotAnEntireWideTab() {
        val config = EditorConfig(indentSize = 2, tabSize = 8, insertSpacesForTabs = false)
        val edit = resolveEditorLineIndentation("\twork();", config, outdent = true)
        assertThat(edit.oldLength).isEqualTo(1)
        assertThat(edit.text).isEqualTo("      ")
    }

    @Test
    fun selectedLines_indentAndOutdentRoundTripAndUndoRemainsAtomic() {
        val state = state("  one();\n  two();", EditorConfig(indentSize = 2, tabSize = 8))
        state.selectRange(0, state.textBuffer.length)
        editorIndentOrOutdentSelectionByTab(state, outdent = false)
        assertThat(state.textBuffer.toString()).isEqualTo("    one();\n    two();")
        editorIndentOrOutdentSelectionByTab(state, outdent = true)
        assertThat(state.textBuffer.toString()).isEqualTo("  one();\n  two();")
        state.undo()
        assertThat(state.textBuffer.toString()).isEqualTo("    one();\n    two();")
    }

    @Test
    fun multipleCursors_useTheSameWidthsAndRestoreSelectionsOnUndo() {
        val state = state("  one();\n  two();", EditorConfig(indentSize = 2, tabSize = 8))
        val selections = EditorSelectionSet.of(OffsetRange(4, 4), listOf(OffsetRange(13, 13)))
        state.applySelectionSet(selections, ensureVisible = false)
        editorIndentOrOutdentSelectionByTab(state, outdent = false)
        assertThat(state.textBuffer.toString()).isEqualTo("    one();\n    two();")
        assertThat(state.selectionSet.selections).containsExactly(OffsetRange(6, 6), OffsetRange(17, 17)).inOrder()
        state.undo()
        assertThat(state.textBuffer.toString()).isEqualTo("  one();\n  two();")
        assertThat(state.selectionSet).isEqualTo(selections)
    }

    @Test
    fun parenthesisNewline_usesProjectContinuationWidth() {
        val state = state("  call(", EditorConfig(indentSize = 4, tabSize = 8, continuationIndentSize = 2))
        state.insertUserInput("\n")
        assertThat(state.textBuffer.toString()).isEqualTo("  call(\n    ")
    }

    private fun state(text: String, config: EditorConfig): EditorState =
        EditorState(RopeTextBuffer(text), config = config).apply { moveCursorTo(textBuffer.length) }
}
