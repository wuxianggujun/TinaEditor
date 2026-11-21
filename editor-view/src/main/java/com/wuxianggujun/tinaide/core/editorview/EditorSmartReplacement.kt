package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextScanKernel

/**
 * 统一的"智能插入"规则（IME commitText / 硬件键盘 onPreviewKeyEvent 共用）。
 *
 * 覆盖：
 * - Tab：按 tab stop 转空格（可选）
 * - Enter：最小版自动缩进（可选），并支持 `{|}` 双行骨架
 * - 自动闭合括号：`()` `[]` `{}` `""` `''`
 * - 跳过闭合：光标后已有对应闭合符时直接跳过
 */
internal data class EditorResolvedReplacement(
    val replacement: String,
    val cursorOffsetAfterInsert: Int?
)

internal object EditorSmartReplacement {

    private val AUTO_CLOSE_PAIRS = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '"' to '"',
        '\'' to '\''
    )

    private val CLOSE_CHARS = setOf(')', ']', '}', '"', '\'')

    fun resolve(
        state: EditorState,
        startOffset: Int,
        replacement: String,
        endOffset: Int = startOffset,
    ): EditorResolvedReplacement {
        val hasSelection = endOffset != startOffset
        if (replacement == "\t") {
            val startPosition = runCatching { state.textBuffer.offsetToPosition(startOffset) }.getOrNull()
                ?: return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)
            val line = startPosition.line.coerceIn(0, (state.textBuffer.lineCount - 1).coerceAtLeast(0))
            val lineText = state.textBuffer.getLine(line)
            val column = startPosition.column.coerceIn(0, lineText.length)
            val tabSize = state.config.tabSize.coerceAtLeast(1)
            val visualColumn = resolveVisualColumnForTabStops(lineText, column, tabSize)
            val leadingWhitespaceEnd = TextScanKernel.scanLineWhitespace(lineText, tabSize).leadingWhitespaceEnd
            val isIndentation = column <= leadingWhitespaceEnd
            if (!isIndentation && !state.config.insertSpacesForTabs) {
                return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)
            }
            if (isIndentation && state.config.indentSize <= 0) {
                return EditorResolvedReplacement("", cursorOffsetAfterInsert = null)
            }
            val step = if (isIndentation) state.config.indentSize.coerceAtLeast(1) else tabSize
            val spaces = (step - (visualColumn % step)).coerceIn(1, step)
            return EditorResolvedReplacement(
                buildEditorIndentationFromColumn(visualColumn, spaces, state.config),
                cursorOffsetAfterInsert = null,
            )
        }

        if (replacement.length == 1) {
            val ch = replacement[0]

            val closeChar = AUTO_CLOSE_PAIRS[ch]
            if (closeChar != null) {
                val charAfterCursor = charAfterOffset(state, startOffset)
                if (ch == '"' || ch == '\'') {
                    if (!hasSelection && charAfterCursor == ch) {
                        return EditorResolvedReplacement(
                            replacement = "",
                            cursorOffsetAfterInsert = startOffset + 1
                        )
                    }
                    val charBeforeCursor = charBeforeOffset(state, startOffset)
                    if (charBeforeCursor != null && (charBeforeCursor.isLetterOrDigit() || charBeforeCursor == ch)) {
                        return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)
                    }
                }
                return EditorResolvedReplacement(
                    replacement = "$ch$closeChar",
                    cursorOffsetAfterInsert = startOffset + 1
                )
            }

            if (ch in CLOSE_CHARS) {
                val charAfterCursor = charAfterOffset(state, startOffset)
                if (!hasSelection && charAfterCursor == ch) {
                    return EditorResolvedReplacement(
                        replacement = "",
                        cursorOffsetAfterInsert = startOffset + 1
                    )
                }
            }
        }

        if (replacement != "\n") return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)
        if (!state.config.autoIndent) return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)

        val startPosition = runCatching { state.textBuffer.offsetToPosition(startOffset) }.getOrNull()
            ?: return EditorResolvedReplacement(replacement, cursorOffsetAfterInsert = null)
        val line = startPosition.line.coerceIn(0, (state.textBuffer.lineCount - 1).coerceAtLeast(0))
        val lineText = state.textBuffer.getLine(line)
        val column = startPosition.column.coerceIn(0, lineText.length)
        val lineWhitespaceInfo = TextScanKernel.scanLineWhitespace(lineText, state.config.tabSize)

        val baseIndent = lineText.substring(0, lineWhitespaceInfo.leadingWhitespaceEnd)
        val before = lineText.substring(0, column)
        val after = lineText.substring(column)
        val trimmedBefore = before.substring(
            startIndex = 0,
            endIndex = TextScanKernel.scanLineWhitespace(before, state.config.tabSize).trailingWhitespaceStart
        )
        val trimmedAfter = after.substring(
            startIndex = TextScanKernel.scanLineWhitespace(after, state.config.tabSize).leadingWhitespaceEnd
        )

        val opensBlock = trimmedBefore.endsWith("{")
        val opensContinuation = trimmedBefore.endsWith("(") || trimmedBefore.endsWith("[")
        val shouldIndentMore = opensBlock || opensContinuation
        val extraIndentWidth = when {
            opensBlock -> state.config.indentSize
            opensContinuation -> state.config.continuationIndentSize
            else -> 0
        }.coerceAtLeast(0)

        val closingAfter = trimmedAfter.startsWith("}") ||
            trimmedAfter.startsWith(")") ||
            trimmedAfter.startsWith("]")

        val nextIndent = if (shouldIndentMore && extraIndentWidth > 0) {
            buildEditorIndentation(
                lineWhitespaceInfo.leadingIndentColumns + extraIndentWidth,
                state.config,
            )
        } else {
            baseIndent
        }

        if (shouldIndentMore && closingAfter) {
            val resolvedReplacement = "\n" + nextIndent + "\n" + baseIndent
            val cursorOffset = (startOffset + 1 + nextIndent.length)
                .coerceIn(0, state.textBuffer.length + resolvedReplacement.length)
            return EditorResolvedReplacement(resolvedReplacement, cursorOffsetAfterInsert = cursorOffset)
        }

        val resolvedReplacement = "\n" + nextIndent
        // The generated indentation is part of the user-visible newline edit. Some IMEs
        // report newCursorPosition == 0 for Enter, which would otherwise leave the caret
        // before these spaces and make the next character appear before the indentation.
        return EditorResolvedReplacement(
            resolvedReplacement,
            cursorOffsetAfterInsert = startOffset + resolvedReplacement.length
        )
    }

    private fun charAfterOffset(state: EditorState, offset: Int): Char? {
        if (offset < 0 || offset >= state.textBuffer.length) return null
        return state.textBuffer.charAt(offset)
    }

    private fun charBeforeOffset(state: EditorState, offset: Int): Char? {
        if (offset <= 0 || offset > state.textBuffer.length) return null
        return state.textBuffer.charAt(offset - 1)
    }

    private fun resolveVisualColumnForTabStops(
        lineText: String,
        column: Int,
        tabSize: Int
    ): Int = TextScanKernel.measureVisualColumns(lineText, tabSize, column)
}
