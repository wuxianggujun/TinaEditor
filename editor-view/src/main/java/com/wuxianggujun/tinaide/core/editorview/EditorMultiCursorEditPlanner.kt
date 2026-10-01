package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextBuffer

data class EditorTextEdit(
    val start: Int,
    val end: Int,
    val replacement: String
) {
    init {
        require(start >= 0) { "Edit start must not be negative" }
        require(end >= start) { "Edit end must not precede start" }
    }
}

data class EditorEditPlan(
    val edits: List<EditorTextEdit>,
    val resultingSelections: EditorSelectionSet
) {
    val isEmpty: Boolean
        get() = edits.isEmpty()
}

/**
 * Creates simultaneous edits for a selection/cursor set.
 *
 * Returned edits are ordered from low to high offset. Callers must apply them in reverse
 * order so an edit cannot invalidate the offsets of a later edit. Overlapping selections
 * are represented by one edit and therefore cannot duplicate inserted text.
 */
object EditorMultiCursorEditPlanner {
    fun replace(
        selectionSet: EditorSelectionSet,
        replacement: String,
        documentLength: Int,
        primaryCaretOverride: Int? = null
    ): EditorEditPlan = plan(
        selectionSet = selectionSet,
        documentLength = documentLength,
        replacement = replacement,
        primaryCaretOverride = primaryCaretOverride,
        resolveRange = { range, _ -> range.start to range.end }
    )

    fun backspace(
        selectionSet: EditorSelectionSet,
        textBuffer: TextBuffer
    ): EditorEditPlan = plan(
        selectionSet = selectionSet,
        documentLength = textBuffer.length,
        replacement = "",
        resolveRange = { range, _ ->
            if (!range.isEmpty) {
                range.start to range.end
            } else {
                val deleteLength = editorUnitLengthBefore(textBuffer, range.caret)
                (range.caret - deleteLength) to range.caret
            }
        }
    )

    fun deleteForward(
        selectionSet: EditorSelectionSet,
        textBuffer: TextBuffer
    ): EditorEditPlan = plan(
        selectionSet = selectionSet,
        documentLength = textBuffer.length,
        replacement = "",
        resolveRange = { range, _ ->
            if (!range.isEmpty) {
                range.start to range.end
            } else {
                val deleteLength = editorUnitLengthAfter(textBuffer, range.caret)
                range.caret to (range.caret + deleteLength)
            }
        }
    )

    private fun plan(
        selectionSet: EditorSelectionSet,
        documentLength: Int,
        replacement: String,
        primaryCaretOverride: Int? = null,
        resolveRange: (OffsetRange, Int) -> Pair<Int, Int>
    ): EditorEditPlan {
        val normalized = selectionSet.normalized(documentLength)
        val requestedEdits = normalized.selections.mapIndexed { index, selection ->
            val (rawStart, rawEnd) = resolveRange(selection, index)
            RequestedEdit(
                selectionIndex = index,
                start = rawStart.coerceIn(0, documentLength),
                end = rawEnd.coerceIn(0, documentLength).coerceAtLeast(rawStart.coerceIn(0, documentLength)),
                replacement = replacement
            )
        }.sortedWith(
            compareBy<RequestedEdit> { it.start }
                .thenByDescending { it.end }
                .thenBy { it.selectionIndex }
        )

        val groups = mergeOverlappingEdits(requestedEdits)
        val resultingRanges = ArrayList<OffsetRange>(groups.size)
        val actualEdits = ArrayList<EditorTextEdit>(groups.size)
        var cumulativeDelta = 0
        var primaryResult: OffsetRange? = null
        val primaryGroup = groups.firstOrNull { normalized.primaryIndex in it.selectionIndices }

        for (group in groups) {
            val outputStart = group.start + cumulativeDelta
            val outputCaret = if (
                primaryCaretOverride != null && group === primaryGroup
            ) {
                (primaryCaretOverride + cumulativeDelta).coerceIn(
                    0,
                    documentLength + cumulativeDelta + replacement.length - (group.end - group.start)
                )
            } else {
                outputStart + replacement.length
            }
            val result = OffsetRange(outputCaret, outputCaret)
            resultingRanges += result
            if (group === primaryGroup) {
                primaryResult = result
            }
            if (group.start < group.end || replacement.isNotEmpty()) {
                actualEdits += EditorTextEdit(
                    start = group.start,
                    end = group.end,
                    replacement = replacement
                )
            }
            cumulativeDelta += replacement.length - (group.end - group.start)
        }

        val resolvedPrimary = primaryResult ?: OffsetRange(
            normalized.primary.caret,
            normalized.primary.caret
        )
        val secondaryResults = resultingRanges.filterNot { it == resolvedPrimary }
        return EditorEditPlan(
            edits = actualEdits,
            resultingSelections = EditorSelectionSet.of(
                primary = resolvedPrimary,
                secondary = secondaryResults
            )
        )
    }

    private fun mergeOverlappingEdits(
        requestedEdits: List<RequestedEdit>
    ): List<EditGroup> {
        if (requestedEdits.isEmpty()) return emptyList()

        val groups = ArrayList<EditGroup>()
        for (requested in requestedEdits) {
            val previous = groups.lastOrNull()
            if (previous != null && overlaps(previous, requested)) {
                previous.end = maxOf(previous.end, requested.end)
                previous.selectionIndices += requested.selectionIndex
            } else {
                groups += EditGroup(
                    start = requested.start,
                    end = requested.end,
                    selectionIndices = mutableListOf(requested.selectionIndex)
                )
            }
        }
        return groups
    }

    private fun overlaps(previous: EditGroup, next: RequestedEdit): Boolean =
        next.start < previous.end || next.start == previous.start

    private data class RequestedEdit(
        val selectionIndex: Int,
        val start: Int,
        val end: Int,
        val replacement: String
    )

    private data class EditGroup(
        val start: Int,
        var end: Int,
        val selectionIndices: MutableList<Int>
    )
}
