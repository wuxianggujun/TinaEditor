package com.wuxianggujun.tinaide.core.editorview

/**
 * Immutable selection/cursor collection used by multi-cursor editing.
 *
 * [primaryIndex] identifies the selection that remains compatible with the legacy
 * [EditorState.cursorOffset] and [EditorState.selectionRange] API. The collection is
 * canonicalized by document order and exact duplicate ranges are removed.
 */
@ConsistentCopyVisibility
data class EditorSelectionSet private constructor(
    val selections: List<OffsetRange>,
    val primaryIndex: Int
) {
    init {
        require(selections.isNotEmpty()) { "Selection set must contain a primary selection" }
        require(primaryIndex in selections.indices) { "Primary selection index is out of bounds" }
    }

    val primary: OffsetRange
        get() = selections[primaryIndex]

    val secondary: List<OffsetRange>
        get() = selections.filterIndexed { index, _ -> index != primaryIndex }

    val isMultiCursor: Boolean
        get() = selections.size > 1

    /** Clamp offsets and restore document-order/duplicate invariants. */
    fun normalized(documentLength: Int): EditorSelectionSet {
        val safeLength = documentLength.coerceAtLeast(0)
        val clamped = selections.map { range ->
            OffsetRange(
                anchor = range.anchor.coerceIn(0, safeLength),
                caret = range.caret.coerceIn(0, safeLength)
            )
        }
        return canonicalize(clamped, primaryIndex)
    }

    companion object {
        fun single(range: OffsetRange = OffsetRange(0, 0)): EditorSelectionSet =
            EditorSelectionSet(listOf(range), primaryIndex = 0)

        fun of(
            primary: OffsetRange,
            secondary: List<OffsetRange> = emptyList()
        ): EditorSelectionSet = canonicalize(
            ranges = listOf(primary) + secondary,
            primaryIndex = 0
        )

        private fun canonicalize(
            ranges: List<OffsetRange>,
            primaryIndex: Int
        ): EditorSelectionSet {
            require(ranges.isNotEmpty()) { "Selection set must contain a primary selection" }
            require(primaryIndex in ranges.indices) { "Primary selection index is out of bounds" }

            val indexedRanges = ranges.mapIndexed { index, range ->
                IndexedRange(index = index, range = range)
            }.sortedWith(
                compareBy<IndexedRange> { it.range.start }
                    .thenByDescending { it.range.end }
                    .thenBy { it.range.anchor }
                    .thenBy { it.index }
            )
            val uniqueRanges = ArrayList<OffsetRange>(ranges.size)
            for (indexedRange in indexedRanges) {
                if (uniqueRanges.lastOrNull() != indexedRange.range) {
                    uniqueRanges += indexedRange.range
                }
            }

            val primaryRange = ranges[primaryIndex]
            val canonicalPrimaryIndex = uniqueRanges.indexOf(primaryRange)
                .takeIf { it >= 0 }
                ?: error("Canonical selection set lost its primary selection")
            return EditorSelectionSet(
                selections = uniqueRanges.toList(),
                primaryIndex = canonicalPrimaryIndex
            )
        }
    }

    private data class IndexedRange(
        val index: Int,
        val range: OffsetRange
    )
}
