package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test

class EditorMultiCursorEditPlannerTest {

    @Test
    fun selectionSet_shouldSortAndKeepPrimaryIdentity() {
        val selections = EditorSelectionSet.of(
            primary = OffsetRange(5, 5),
            secondary = listOf(OffsetRange(1, 1), OffsetRange(1, 1))
        )

        assertThat(selections.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(5, 5)
        ).inOrder()
        assertThat(selections.primary).isEqualTo(OffsetRange(5, 5))
        assertThat(selections.primaryIndex).isEqualTo(1)
    }

    @Test
    fun replace_shouldInsertAtEveryCursorAndTranslateResultingCursors() {
        val selectionSet = EditorSelectionSet.of(
            primary = OffsetRange(0, 0),
            secondary = listOf(OffsetRange(4, 4))
        )

        val plan = EditorMultiCursorEditPlanner.replace(
            selectionSet = selectionSet,
            replacement = "X",
            documentLength = 7
        )

        assertThat(plan.edits).containsExactly(
            EditorTextEdit(0, 0, "X"),
            EditorTextEdit(4, 4, "X")
        ).inOrder()
        assertThat(plan.resultingSelections.selections).containsExactly(
            OffsetRange(1, 1),
            OffsetRange(6, 6)
        ).inOrder()
        assertThat(plan.resultingSelections.primary).isEqualTo(OffsetRange(1, 1))
    }

    @Test
    fun replace_shouldMergeOverlappingSelectionsIntoOneEdit() {
        val selectionSet = EditorSelectionSet.of(
            primary = OffsetRange(1, 4),
            secondary = listOf(OffsetRange(3, 5))
        )

        val plan = EditorMultiCursorEditPlanner.replace(
            selectionSet = selectionSet,
            replacement = "X",
            documentLength = 8
        )

        assertThat(plan.edits).containsExactly(EditorTextEdit(1, 5, "X"))
        assertThat(plan.resultingSelections.selections).containsExactly(OffsetRange(2, 2))
    }

    @Test
    fun backspace_shouldDeleteWholeCrLfUnitAtEveryCursor() {
        val buffer = RopeTextBuffer("a\r\nb\r\nc")
        val selectionSet = EditorSelectionSet.of(
            primary = OffsetRange(3, 3),
            secondary = listOf(OffsetRange(6, 6))
        )

        val plan = EditorMultiCursorEditPlanner.backspace(selectionSet, buffer)

        assertThat(plan.edits).containsExactly(
            EditorTextEdit(1, 3, ""),
            EditorTextEdit(4, 6, "")
        ).inOrder()
    }

    @Test
    fun deleteForward_shouldDeleteWholeSurrogatePairUnit() {
        val buffer = RopeTextBuffer("A😀B😀C")
        val selectionSet = EditorSelectionSet.of(
            primary = OffsetRange(1, 1),
            secondary = listOf(OffsetRange(4, 4))
        )

        val plan = EditorMultiCursorEditPlanner.deleteForward(selectionSet, buffer)

        assertThat(plan.edits).containsExactly(
            EditorTextEdit(1, 3, ""),
            EditorTextEdit(4, 6, "")
        ).inOrder()
    }

    @Test
    fun replace_shouldApplyEditsBackwardsWithoutOffsetDrift() {
        val buffer = RopeTextBuffer("one two")
        val selectionSet = EditorSelectionSet.of(
            primary = OffsetRange(0, 0),
            secondary = listOf(OffsetRange(4, 4))
        )
        val plan = EditorMultiCursorEditPlanner.replace(
            selectionSet = selectionSet,
            replacement = "X",
            documentLength = buffer.length
        )

        buffer.editTransaction {
            plan.edits.asReversed().forEach { edit ->
                replace(edit.start, edit.end, edit.replacement)
            }
        }

        assertThat(buffer.toString()).isEqualTo("Xone Xtwo")
    }
}
