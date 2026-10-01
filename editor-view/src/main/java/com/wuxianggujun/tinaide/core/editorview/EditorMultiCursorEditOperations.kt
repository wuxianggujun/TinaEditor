package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextSelectionRangeSnapshot
import com.wuxianggujun.tinaide.core.textengine.TextSelectionSnapshot

internal fun editorReplaceMultipleSelections(
    state: EditorState,
    replacement: String,
    reason: String,
    primaryCaretOverride: Int? = null
): Boolean {
    if (!state.hasMultipleSelections) return false
    val plan = EditorMultiCursorEditPlanner.replace(
        selectionSet = state.selectionSet,
        replacement = replacement,
        documentLength = state.textBuffer.length,
        primaryCaretOverride = primaryCaretOverride
    )
    return applyMultiCursorEditPlan(state, plan, reason)
}

internal fun editorBackspaceMultipleSelections(state: EditorState): Boolean {
    if (!state.hasMultipleSelections) return false
    val plan = EditorMultiCursorEditPlanner.backspace(state.selectionSet, state.textBuffer)
    return applyMultiCursorEditPlan(state, plan, reason = "backspace")
}

internal fun editorDeleteForwardMultipleSelections(state: EditorState): Boolean {
    if (!state.hasMultipleSelections) return false
    val plan = EditorMultiCursorEditPlanner.deleteForward(state.selectionSet, state.textBuffer)
    return applyMultiCursorEditPlan(state, plan, reason = "deleteForward")
}

internal fun editorDeleteSurroundingMultipleSelections(
    state: EditorState,
    reason: String,
    resolveRange: (Int) -> Pair<Int, Int>?
): Boolean {
    if (!state.hasMultipleSelections) return false
    val plan = EditorMultiCursorEditPlanner.deleteSurrounding(
        selectionSet = state.selectionSet,
        documentLength = state.textBuffer.length,
        resolveRange = resolveRange
    )
    return applyMultiCursorEditPlan(state, plan, reason)
}

private fun applyMultiCursorEditPlan(
    state: EditorState,
    plan: EditorEditPlan,
    reason: String
): Boolean {
    if (plan.isEmpty) return false
    val selectionBefore = state.selectionSet
    val selectionAfter = plan.resultingSelections
    state.cancelSnippet()
    state.textBuffer.editTransaction(
        cursorBefore = selectionBefore.primary.caret,
        cursorAfter = { selectionAfter.primary.caret },
        selectionBefore = selectionBefore.toTextSelectionSnapshot(),
        selectionAfter = { selectionAfter.toTextSelectionSnapshot() }
    ) {
        plan.edits.asReversed().forEach { edit ->
            replace(
                start = edit.start,
                end = edit.end,
                text = edit.replacement
            )
        }
    }
    state.applySelectionSet(selectionAfter, ensureVisible = true)
    state.emitTextChanged(reason)
    return true
}

internal fun EditorSelectionSet.toTextSelectionSnapshot(): TextSelectionSnapshot =
    TextSelectionSnapshot(
        anchor = primary.anchor,
        caret = primary.caret,
        additionalSelections = secondary.map { selection ->
            TextSelectionRangeSnapshot(
                anchor = selection.anchor,
                caret = selection.caret
            )
        }
    )
