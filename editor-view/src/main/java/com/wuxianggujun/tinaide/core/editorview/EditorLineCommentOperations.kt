package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextScanKernel

internal data class EditorLineCommentTarget(
    val lineStartOffset: Int,
    val text: String
)

internal data class EditorLineCommentEdit(
    val offset: Int,
    val oldLength: Int,
    val replacement: String
) {
    val newLength: Int get() = replacement.length
    val delta: Int get() = newLength - oldLength
}

internal fun buildLineCommentEdits(
    lineInfos: List<EditorLineCommentTarget>,
    commentToken: String,
    tabSize: Int
): List<EditorLineCommentEdit> {
    if (commentToken.isBlank()) return emptyList()

    val shouldUncomment = lineInfos
        .asSequence()
        .map { it.text }
        .filter { it.isNotBlank() }
        .all { line ->
            val indent = TextScanKernel
                .scanLineWhitespace(line, tabSize)
                .leadingWhitespaceEnd
            line.substring(indent).startsWith(commentToken)
        }

    return lineInfos.mapNotNull { lineInfo ->
        val line = lineInfo.text
        if (line.isBlank()) return@mapNotNull null

        val indent = TextScanKernel
            .scanLineWhitespace(line, tabSize)
            .leadingWhitespaceEnd
        val rest = line.substring(indent)
        when {
            shouldUncomment && rest.startsWith(commentToken) -> {
                val afterToken = rest.drop(commentToken.length)
                val optionalSpaceLength = if (afterToken.startsWith(" ")) 1 else 0
                EditorLineCommentEdit(
                    offset = lineInfo.lineStartOffset + indent,
                    oldLength = commentToken.length + optionalSpaceLength,
                    replacement = ""
                )
            }

            !shouldUncomment && !rest.startsWith(commentToken) -> {
                EditorLineCommentEdit(
                    offset = lineInfo.lineStartOffset + indent,
                    oldLength = 0,
                    replacement = "$commentToken "
                )
            }

            else -> null
        }
    }
}

internal fun adjustOffsetAfterLineCommentEdits(
    offset: Int,
    edits: List<EditorLineCommentEdit>,
    textLength: Int
): Int {
    var delta = 0
    for (edit in edits.sortedBy { it.offset }) {
        if (edit.oldLength == 0) {
            if (offset >= edit.offset) {
                delta += edit.newLength
            }
            continue
        }

        if (offset < edit.offset) break

        val editEnd = edit.offset + edit.oldLength
        if (offset <= editEnd) {
            return (edit.offset + delta).coerceIn(0, textLength)
        }
        delta += edit.delta
    }
    return (offset + delta).coerceIn(0, textLength)
}
