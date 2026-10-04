package com.wuxianggujun.tinaide.core.editorview

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class EditorFindOptions(
    val caseSensitive: Boolean = false,
    val regex: Boolean = false,
    val wholeWord: Boolean = false
)

enum class EditorFindError { InvalidQuery, InvalidReplacement }

internal data class EditorFindResult(
    val matches: List<OffsetRange> = emptyList(),
    val edits: List<EditorTextEdit> = emptyList(),
    val error: EditorFindError? = null
)

/** One matcher defines both navigation and replacement, including zero-width matches. */
internal object EditorFindEngine {
    fun scan(
        text: String,
        query: String,
        options: EditorFindOptions,
        replacement: String? = null,
        onlyRange: OffsetRange? = null,
        checkCancelled: () -> Unit = {}
    ): EditorFindResult {
        if (query.isEmpty()) return EditorFindResult()
        val pattern = try {
            Pattern.compile(
                if (options.regex) query else Pattern.quote(query),
                if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            )
        } catch (_: PatternSyntaxException) {
            return EditorFindResult(error = EditorFindError.InvalidQuery)
        }
        val matcher = pattern.matcher(text)
        val matches = ArrayList<OffsetRange>()
        val edits = ArrayList<EditorTextEdit>()
        val expanded = StringBuffer()
        var appendPosition = 0
        while (matcher.find()) {
            checkCancelled()
            val start = matcher.start()
            val end = matcher.end()
            if (options.wholeWord && !isWholeWord(text, start, end)) continue
            val range = OffsetRange(start, end)
            matches += range
            if (replacement == null || (onlyRange != null && onlyRange != range)) continue
            val value = if (options.regex) {
                expanded.setLength(0)
                try {
                    matcher.appendReplacement(expanded, replacement)
                } catch (_: IllegalArgumentException) {
                    return EditorFindResult(error = EditorFindError.InvalidReplacement)
                } catch (_: IndexOutOfBoundsException) {
                    return EditorFindResult(error = EditorFindError.InvalidReplacement)
                }
                // appendReplacement also appends the unchanged gap since its previous call.
                expanded.substring(start - appendPosition).also { appendPosition = end }
            } else replacement
            if (text.substring(start, end) != value) edits += EditorTextEdit(start, end, value)
        }
        return EditorFindResult(matches, edits)
    }

    private fun isWholeWord(text: String, start: Int, end: Int): Boolean =
        (start == 0 || !isWordCharacter(text.codePointBefore(start))) &&
            (end == text.length || !isWordCharacter(text.codePointAt(end)))

    private fun isWordCharacter(codePoint: Int): Boolean =
        codePoint == '_'.code || Character.isLetterOrDigit(codePoint)
}
