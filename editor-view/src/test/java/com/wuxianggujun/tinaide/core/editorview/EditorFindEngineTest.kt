package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorFindEngineTest {
    @Test fun literalMatchesDoNotOverlapAndPreserveUnicodeOffsets() {
        assertThat(EditorFindEngine.scan("aaaa", "aa", EditorFindOptions()).matches)
            .containsExactly(OffsetRange(0, 2), OffsetRange(2, 4)).inOrder()
        assertThat(EditorFindEngine.scan("İ foo FOO", "foo", EditorFindOptions()).matches)
            .containsExactly(OffsetRange(2, 5), OffsetRange(6, 9)).inOrder()
    }

    @Test fun wholeWordUsesUnicodeCodePoints() {
        val text = "foo foobar foo_ 中文foo foo"
        val result = EditorFindEngine.scan(text, "foo", EditorFindOptions(wholeWord = true))
        assertThat(result.matches).containsExactly(OffsetRange(0, 3), OffsetRange(text.length - 3, text.length))
    }

    @Test fun regexGroupsAndSkippedWholeWordMatchesHaveCorrectReplacement() {
        val result = EditorFindEngine.scan("xa1 a2 a3", "a(\\d)",
            EditorFindOptions(regex = true, wholeWord = true), "b$1")
        assertThat(result.edits).containsExactly(EditorTextEdit(4, 6, "b2"), EditorTextEdit(7, 9, "b3")).inOrder()
    }

    @Test fun currentReplacementCanExpandGroupsAfterUnselectedMatches() {
        val result = EditorFindEngine.scan("a1 a2 a3", "a(?<n>\\d)",
            EditorFindOptions(regex = true), "b\${n}", OffsetRange(6, 8))
        assertThat(result.edits).containsExactly(EditorTextEdit(6, 8, "b3"))
    }

    @Test fun zeroWidthRegexTerminatesAndCanInsertAtBothEnds() {
        val result = EditorFindEngine.scan("abc", "^|$", EditorFindOptions(regex = true), "|")
        assertThat(result.edits).containsExactly(EditorTextEdit(0, 0, "|"), EditorTextEdit(3, 3, "|")).inOrder()
    }

    @Test fun invalidPatternsAndGroupsAreExplicitErrorsWithoutPartialEdits() {
        assertThat(EditorFindEngine.scan("abc", "[", EditorFindOptions(regex = true)).error)
            .isEqualTo(EditorFindError.InvalidQuery)
        val result = EditorFindEngine.scan("a a", "a", EditorFindOptions(regex = true), "$3")
        assertThat(result.error).isEqualTo(EditorFindError.InvalidReplacement)
        assertThat(result.edits).isEmpty()
    }

    @Test fun literalReplacementDoesNotInterpretDollarOrBackslash() {
        assertThat(EditorFindEngine.scan("foo", "foo", EditorFindOptions(), "$1\\x").edits)
            .containsExactly(EditorTextEdit(0, 3, "$1\\x"))
    }
}
