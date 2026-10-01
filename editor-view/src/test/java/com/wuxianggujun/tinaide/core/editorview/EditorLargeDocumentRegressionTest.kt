package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.textengine.TextChange
import com.wuxianggujun.tinaide.core.treesitter.TreeSitterFoldingProvider.FoldRegion
import org.junit.Test

class EditorLargeDocumentRegressionTest {

    @Test
    fun oneHundredThousandLines_shouldSupportHeadTailEditsAndViewportQueries() {
        val lineCount = 100_000
        val initialText = buildString {
            repeat(lineCount) { line ->
                append("line-")
                append(line)
                if (line < lineCount - 1) append('\n')
            }
        }
        val buffer = RopeTextBuffer(initialText)
        val state = EditorState(
            textBuffer = buffer,
            config = EditorConfig(
                wordWrap = false,
                codeFolding = true,
                rainbowBrackets = false,
                bracketPairGuides = false
            )
        )
        state.updateMetrics(
            lineHeightPx = 20f,
            charWidthPx = 8f,
            viewportHeightPx = 400f,
            viewportWidthPx = 800f,
            contentStartXPx = 48f
        )

        state.gotoLine(line = 0, column = 0)
        assertThat(state.cursorPosition.line).isEqualTo(0)
        state.gotoLine(line = lineCount - 1, column = 3)
        assertThat(state.cursorPosition.line).isEqualTo(lineCount - 1)
        assertThat(state.visibleDocumentLines.first).isAtLeast(0)
        assertThat(state.visibleDocumentLines.last).isAtMost(lineCount - 1)

        applyInsert(
            buffer = buffer,
            state = state,
            line = 0,
            column = 0,
            replacement = "head-"
        )
        val tailLine = buffer.getLine(buffer.lineCount - 1)
        applyInsert(
            buffer = buffer,
            state = state,
            line = buffer.lineCount - 1,
            column = tailLine.length,
            replacement = "-tail"
        )

        assertThat(buffer.lineCount).isEqualTo(lineCount)
        assertThat(buffer.getLine(0)).startsWith("head-line-0")
        assertThat(buffer.getLine(lineCount - 1)).endsWith("-tail")
        assertThat(state.observableState.value.documentVersion).isEqualTo(buffer.version)
        assertThat(state.observableState.value.documentLength).isEqualTo(buffer.length)
    }

    @Test
    fun staleFoldVersion_shouldNotReplaceCurrentFoldStateOnLargeDocument() {
        val buffer = RopeTextBuffer((0 until 100_000).joinToString("\n") { "line-$it" })
        val state = EditorState(
            textBuffer = buffer,
            config = EditorConfig(
                wordWrap = false,
                codeFolding = true,
                rainbowBrackets = false,
                bracketPairGuides = false
            )
        )

        val currentVersion = buffer.version
        state.setFoldRegions(
            regions = listOf(FoldRegion(startLine = 0, endLine = 3)),
            documentVersion = currentVersion
        )
        state.toggleFoldAtLine(0)
        assertThat(state.isFoldCollapsedAtLine(0)).isTrue()

        state.setFoldRegions(
            regions = emptyList(),
            documentVersion = currentVersion - 1
        )
        assertThat(state.isFoldCollapsedAtLine(0)).isTrue()
    }

    private fun applyInsert(
        buffer: RopeTextBuffer,
        state: EditorState,
        line: Int,
        column: Int,
        replacement: String
    ) {
        val offset = buffer.positionToOffset(line, column)
        buffer.insert(offset, replacement)
        state.applyTextBufferChange(
            TextChange(
                startOffset = offset,
                endOffset = offset,
                oldText = "",
                newText = replacement,
                startLine = line,
                startColumn = column,
                endLine = line,
                endColumn = column,
                documentVersion = buffer.version
            )
        )
    }

}
