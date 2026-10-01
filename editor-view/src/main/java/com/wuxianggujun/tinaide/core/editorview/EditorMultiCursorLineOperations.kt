package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextScanKernel

internal fun editorToggleLineCommentMultipleSelections(
    state: EditorState,
    commentToken: String
): Boolean {
    if (!state.hasMultipleSelections || commentToken.isBlank()) return false

    val resolution = resolveMultiCursorLineBlocks(state)
    val lineInfos = resolution.blocks.flatMap { block ->
        (block.startLine..block.endLine).map { line ->
            EditorLineCommentTarget(
                lineStartOffset = state.textBuffer.getLineStart(line),
                text = state.textBuffer.getLine(line)
            )
        }
    }
    if (lineInfos.isEmpty()) return false

    val commentEdits = buildLineCommentEdits(
        lineInfos = lineInfos,
        commentToken = commentToken,
        tabSize = state.config.tabSize
    )
    if (commentEdits.isEmpty()) return false

    val edits = commentEdits.map { edit ->
        EditorTextEdit(
            start = edit.offset,
            end = edit.offset + edit.oldLength,
            replacement = edit.replacement
        )
    }

    val selectionAfter = mapSelectionSetThroughEdits(
        selectionSet = state.selectionSet,
        edits = edits
    )
    return applyMultiCursorLineEditPlan(
        state = state,
        edits = edits,
        selectionAfter = selectionAfter,
        reason = "toggleLineComment"
    )
}

internal fun editorIndentOrOutdentMultipleSelectionsByTab(
    state: EditorState,
    outdent: Boolean
): Boolean {
    if (!state.hasMultipleSelections) return false

    val resolution = resolveMultiCursorLineBlocks(state)
    val indentUnit = if (state.config.insertSpacesForTabs) {
        " ".repeat(state.config.tabSize.coerceAtLeast(1))
    } else {
        "\t"
    }
    val edits = ArrayList<EditorTextEdit>()
    resolution.blocks.forEach { block ->
        for (line in block.startLine..block.endLine) {
            val lineStart = state.textBuffer.getLineStart(line)
            if (!outdent) {
                edits += EditorTextEdit(
                    start = lineStart,
                    end = lineStart,
                    replacement = indentUnit
                )
                continue
            }

            val lineText = state.textBuffer.getLine(line)
            val removeCount = if (lineText.firstOrNull() == '\t') {
                1
            } else {
                TextScanKernel
                    .scanLineWhitespace(lineText, state.config.tabSize)
                    .outdentRemoveCount
            }
            if (removeCount > 0) {
                edits += EditorTextEdit(
                    start = lineStart,
                    end = lineStart + removeCount,
                    replacement = ""
                )
            }
        }
    }
    if (edits.isEmpty()) return false

    val selectionAfter = mapSelectionSetThroughEdits(
        selectionSet = state.selectionSet,
        edits = edits
    )
    return applyMultiCursorLineEditPlan(
        state = state,
        edits = edits,
        selectionAfter = selectionAfter,
        reason = if (outdent) "outdentLines" else "indentLines"
    )
}

internal fun editorDuplicateMultipleSelectedLines(state: EditorState): Boolean {
    if (!state.hasMultipleSelections) return false

    val resolution = resolveMultiCursorLineBlocks(state)
    val lastLine = state.textBuffer.lineCount - 1
    val edits = ArrayList<EditorTextEdit>(resolution.blocks.size)
    val duplicateLengths = IntArray(resolution.blocks.size)
    resolution.blocks.forEachIndexed { index, block ->
        val blockText = state.textBuffer.substring(block.startOffset, block.endOffset)
        val duplicateText = if (block.endLine < lastLine) {
            blockText
        } else {
            lineSeparatorFor(state.textBuffer, block.endLine - 1) + blockText
        }
        duplicateLengths[index] = duplicateText.length
        edits += EditorTextEdit(
            start = block.endOffset,
            end = block.endOffset,
            replacement = duplicateText
        )
    }

    val selectionAfter = mapSelectionSetForDuplicatedBlocks(
        selectionSet = state.selectionSet,
        resolution = resolution,
        duplicateLengths = duplicateLengths
    )
    return applyMultiCursorLineEditPlan(
        state = state,
        edits = edits,
        selectionAfter = selectionAfter,
        reason = "duplicateLines"
    )
}

internal fun editorMoveMultipleSelectedLines(
    state: EditorState,
    direction: Int
): Boolean {
    if (!state.hasMultipleSelections || direction == 0) return false

    val resolution = resolveMultiCursorLineBlocks(state)
    val lastLine = state.textBuffer.lineCount - 1
    val canMove = resolution.blocks.all { block ->
        if (direction < 0) block.startLine > 0 else block.endLine < lastLine
    }
    if (!canMove) return false

    val edits = ArrayList<EditorTextEdit>(resolution.blocks.size)
    val selectionShifts = IntArray(resolution.blocks.size)
    resolution.blocks.forEachIndexed { index, block ->
        if (direction < 0) {
            val previousStart = state.textBuffer.getLineStart(block.startLine - 1)
            val previousText = state.textBuffer.substring(previousStart, block.startOffset)
            val blockText = state.textBuffer.substring(block.startOffset, block.endOffset)
            val separator = lineSeparatorBetween(
                textBuffer = state.textBuffer,
                previousLine = block.startLine - 1,
                nextLine = block.startLine
            )
            val replacement = if (blockText.endsWith('\n')) {
                blockText + previousText
            } else {
                blockText + separator + previousText.removeSuffix(separator)
            }
            edits += EditorTextEdit(
                start = previousStart,
                end = block.endOffset,
                replacement = replacement
            )
            selectionShifts[index] = -previousText.length
        } else {
            val nextEnd = lineEndIncludingSeparator(state.textBuffer, block.endLine + 1)
            val blockText = state.textBuffer.substring(block.startOffset, block.endOffset)
            val nextText = state.textBuffer.substring(block.endOffset, nextEnd)
            val separator = lineSeparatorBetween(
                textBuffer = state.textBuffer,
                previousLine = block.endLine,
                nextLine = block.endLine + 1
            )
            val replacement = if (nextText.endsWith('\n')) {
                nextText + blockText
            } else {
                nextText + separator + blockText.removeSuffix(separator)
            }
            edits += EditorTextEdit(
                start = block.startOffset,
                end = nextEnd,
                replacement = replacement
            )
            selectionShifts[index] = nextText.length +
                if (nextText.endsWith('\n')) 0 else separator.length
        }
    }

    val selectionAfter = mapSelectionSetForMovedBlocks(
        selectionSet = state.selectionSet,
        resolution = resolution,
        selectionShifts = selectionShifts
    )
    return applyMultiCursorLineEditPlan(
        state = state,
        edits = edits,
        selectionAfter = selectionAfter,
        reason = if (direction < 0) "moveLinesUp" else "moveLinesDown"
    )
}

private data class MultiCursorLineBlock(
    val startLine: Int,
    var endLine: Int,
    val startOffset: Int,
    var endOffset: Int,
    val selectionIndices: MutableList<Int>
)

private data class MultiCursorLineBlockResolution(
    val blocks: List<MultiCursorLineBlock>,
    val selectionToBlock: IntArray
)

private data class SelectionLineRange(
    val selectionIndex: Int,
    val startLine: Int,
    val endLine: Int
)

private fun resolveMultiCursorLineBlocks(state: EditorState): MultiCursorLineBlockResolution {
    val textBuffer = state.textBuffer
    val normalized = state.selectionSet.normalized(textBuffer.length)
    val lineRanges = normalized.selections.mapIndexed { index, selection ->
        val (startLine, endLine) = resolveSelectionLineRange(textBuffer, selection)
        SelectionLineRange(
            selectionIndex = index,
            startLine = startLine,
            endLine = endLine
        )
    }.sortedWith(
        compareBy<SelectionLineRange> { it.startLine }
            .thenBy { it.endLine }
            .thenBy { it.selectionIndex }
    )

    val blocks = ArrayList<MultiCursorLineBlock>()
    lineRanges.forEach { range ->
        val previous = blocks.lastOrNull()
        if (previous != null && range.startLine <= previous.endLine + 1) {
            previous.endLine = maxOf(previous.endLine, range.endLine)
            previous.endOffset = lineEndIncludingSeparator(textBuffer, previous.endLine)
            previous.selectionIndices += range.selectionIndex
        } else {
            blocks += MultiCursorLineBlock(
                startLine = range.startLine,
                endLine = range.endLine,
                startOffset = textBuffer.getLineStart(range.startLine),
                endOffset = lineEndIncludingSeparator(textBuffer, range.endLine),
                selectionIndices = mutableListOf(range.selectionIndex)
            )
        }
    }

    val selectionToBlock = IntArray(normalized.selections.size)
    blocks.forEachIndexed { blockIndex, block ->
        block.selectionIndices.forEach { selectionIndex ->
            selectionToBlock[selectionIndex] = blockIndex
        }
    }
    return MultiCursorLineBlockResolution(
        blocks = blocks,
        selectionToBlock = selectionToBlock
    )
}

private fun resolveSelectionLineRange(
    textBuffer: TextBuffer,
    selection: OffsetRange
): Pair<Int, Int> {
    val lastLine = (textBuffer.lineCount - 1).coerceAtLeast(0)
    val startOffset = selection.start.coerceIn(0, textBuffer.length)
    val endOffset = selection.end.coerceIn(startOffset, textBuffer.length)
    val startLine = textBuffer.offsetToPosition(startOffset).line.coerceIn(0, lastLine)
    val rawEndLine = textBuffer.offsetToPosition(endOffset).line.coerceIn(startLine, lastLine)
    val endLine = if (
        !selection.isEmpty &&
        endOffset > startOffset &&
        rawEndLine > startLine &&
        endOffset == textBuffer.getLineStart(rawEndLine)
    ) {
        rawEndLine - 1
    } else {
        rawEndLine
    }
    return startLine to endLine.coerceIn(startLine, lastLine)
}

private fun mapSelectionSetThroughEdits(
    selectionSet: EditorSelectionSet,
    edits: List<EditorTextEdit>
): EditorSelectionSet = rebuildSelectionSet(
    selectionSet = selectionSet,
    mappedRanges = selectionSet.selections.map { selection ->
        OffsetRange(
            anchor = mapOffsetThroughEdits(selection.anchor, edits),
            caret = mapOffsetThroughEdits(selection.caret, edits)
        )
    }
)

private fun mapSelectionSetForDuplicatedBlocks(
    selectionSet: EditorSelectionSet,
    resolution: MultiCursorLineBlockResolution,
    duplicateLengths: IntArray
): EditorSelectionSet {
    val offsetsBeforeBlock = IntArray(resolution.blocks.size)
    for (index in 1 until resolution.blocks.size) {
        offsetsBeforeBlock[index] = offsetsBeforeBlock[index - 1] + duplicateLengths[index - 1]
    }
    return rebuildSelectionSet(
        selectionSet = selectionSet,
        mappedRanges = selectionSet.selections.mapIndexed { selectionIndex, selection ->
            val blockIndex = resolution.selectionToBlock[selectionIndex]
            val delta = offsetsBeforeBlock[blockIndex] + duplicateLengths[blockIndex]
            OffsetRange(
                anchor = selection.anchor + delta,
                caret = selection.caret + delta
            )
        }
    )
}

private fun mapSelectionSetForMovedBlocks(
    selectionSet: EditorSelectionSet,
    resolution: MultiCursorLineBlockResolution,
    selectionShifts: IntArray
): EditorSelectionSet = rebuildSelectionSet(
    selectionSet = selectionSet,
    mappedRanges = selectionSet.selections.mapIndexed { selectionIndex, selection ->
        val shift = selectionShifts[resolution.selectionToBlock[selectionIndex]]
        OffsetRange(
            anchor = selection.anchor + shift,
            caret = selection.caret + shift
        )
    }
)

private fun rebuildSelectionSet(
    selectionSet: EditorSelectionSet,
    mappedRanges: List<OffsetRange>
): EditorSelectionSet {
    val primary = mappedRanges[selectionSet.primaryIndex]
    val secondary = mappedRanges.filterIndexed { index, _ -> index != selectionSet.primaryIndex }
    return EditorSelectionSet.of(primary = primary, secondary = secondary)
}

private fun mapOffsetThroughEdits(
    offset: Int,
    edits: List<EditorTextEdit>
): Int {
    var delta = 0
    for (edit in edits) {
        if (edit.start == edit.end) {
            if (offset < edit.start) break
            delta += edit.replacement.length
            continue
        }

        if (offset < edit.start) break
        val editEnd = edit.end
        if (offset <= editEnd) {
            return edit.start + delta + edit.replacement.length
        }
        delta += edit.replacement.length - (edit.end - edit.start)
    }
    return offset + delta
}

private fun applyMultiCursorLineEditPlan(
    state: EditorState,
    edits: List<EditorTextEdit>,
    selectionAfter: EditorSelectionSet,
    reason: String
): Boolean {
    val changedEdits = edits.filter { edit ->
        state.textBuffer.substring(edit.start, edit.end) != edit.replacement
    }
    if (changedEdits.isEmpty()) return false

    val selectionBefore = state.selectionSet
    state.cancelSnippet()
    state.textBuffer.editTransaction(
        cursorBefore = selectionBefore.primary.caret,
        cursorAfter = { selectionAfter.primary.caret },
        selectionBefore = selectionBefore.toTextSelectionSnapshot(),
        selectionAfter = { selectionAfter.toTextSelectionSnapshot() }
    ) {
        changedEdits.asReversed().forEach { edit ->
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

private fun lineEndIncludingSeparator(textBuffer: TextBuffer, line: Int): Int =
    if (line + 1 < textBuffer.lineCount) {
        textBuffer.getLineStart(line + 1)
    } else {
        textBuffer.length
    }

private fun lineSeparatorBetween(
    textBuffer: TextBuffer,
    previousLine: Int,
    nextLine: Int
): String = textBuffer.substring(
    textBuffer.getLineEnd(previousLine),
    textBuffer.getLineStart(nextLine)
)

private fun lineSeparatorFor(textBuffer: TextBuffer, preferredLine: Int): String {
    val lineCount = textBuffer.lineCount
    val candidates = ArrayList<Int>(lineCount)
    if (preferredLine in 0 until lineCount - 1) candidates += preferredLine
    for (line in 0 until lineCount - 1) {
        if (line != preferredLine) candidates += line
    }
    candidates.forEach { line ->
        val separatorStart = textBuffer.getLineEnd(line)
        val separatorEnd = textBuffer.getLineStart(line + 1)
        if (separatorStart < separatorEnd) {
            return textBuffer.substring(separatorStart, separatorEnd)
        }
    }
    return "\n"
}
