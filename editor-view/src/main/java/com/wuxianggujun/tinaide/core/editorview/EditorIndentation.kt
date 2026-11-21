package com.wuxianggujun.tinaide.core.editorview

import com.wuxianggujun.tinaide.core.textengine.TextScanKernel

internal data class EditorIndentationReplacement(val oldLength: Int, val text: String) {
    val columnDelta: Int get() = text.length - oldLength
}

internal fun buildEditorIndentation(columns: Int, config: EditorConfig): String {
    return buildEditorIndentationFromColumn(0, columns.coerceAtLeast(0), config)
}

internal fun buildEditorIndentationFromColumn(startColumn: Int, columns: Int, config: EditorConfig): String {
    val safeColumns = columns.coerceAtLeast(0)
    if (config.insertSpacesForTabs) return " ".repeat(safeColumns)
    val tabSize = config.tabSize.coerceAtLeast(1)
    val firstTabWidth = tabSize - startColumn.coerceAtLeast(0) % tabSize
    if (safeColumns < firstTabWidth) return " ".repeat(safeColumns)
    val remainder = safeColumns - firstTabWidth
    return "\t" + "\t".repeat(remainder / tabSize) + " ".repeat(remainder % tabSize)
}

internal fun resolveEditorLineIndentation(
    line: String,
    config: EditorConfig,
    outdent: Boolean,
): EditorIndentationReplacement {
    val whitespace = TextScanKernel.scanLineWhitespace(line, config.tabSize)
    val indentSize = config.indentSize.coerceAtLeast(0)
    if (indentSize == 0) {
        return EditorIndentationReplacement(whitespace.leadingWhitespaceEnd, line.take(whitespace.leadingWhitespaceEnd))
    }
    val columns = if (outdent) {
        (whitespace.leadingIndentColumns - indentSize).coerceAtLeast(0)
    } else {
        whitespace.leadingIndentColumns + indentSize
    }
    return EditorIndentationReplacement(
        oldLength = whitespace.leadingWhitespaceEnd,
        text = buildEditorIndentation(columns, config),
    )
}
