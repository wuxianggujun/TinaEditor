package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.treesitter.HighlightLineSegment
import com.wuxianggujun.tinaide.core.treesitter.HighlightSpan
import com.wuxianggujun.tinaide.core.treesitter.HighlightType
import com.wuxianggujun.tinaide.core.treesitter.SyntaxHighlighter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorMinimapRendererTest {

    private val density = Density(1f)
    private val renderer = EditorMinimapRenderer()

    private fun createState(
        text: String,
        showMinimap: Boolean = true,
        wordWrap: Boolean = false,
        viewportHeightPx: Float = 400f,
        viewportWidthPx: Float = 300f,
        lineHeightPx: Float = 40f,
        charWidthPx: Float = 10f
    ): EditorState = EditorState(
        textBuffer = RopeTextBuffer().apply { insert(0, text) },
        config = EditorConfig(showMinimap = showMinimap, wordWrap = wordWrap)
    ).apply {
        updateMetrics(
            lineHeightPx = lineHeightPx,
            charWidthPx = charWidthPx,
            viewportHeightPx = viewportHeightPx,
            viewportWidthPx = viewportWidthPx,
            contentStartXPx = 0f
        )
    }

    private fun lines(count: Int, content: String): String =
        (0 until count).joinToString("\n") { content }

    @Test
    fun calculateLayout_returnsNullWhenMinimapDisabled() {
        val state = createState(lines(20, "val x = 0"), showMinimap = false)
        assertThat(renderer.calculateLayout(state, 400f, 1000f, density)).isNull()
    }

    @Test
    fun calculateLayout_returnsNullWhenDocumentFitsViewport() {
        // 5 行 * 40px = 200px 内容，视口 400px，maxScrollPx 为 0：没有可滚动空间就不画小地图。
        val state = createState(lines(5, "val x = 0"))
        assertThat(renderer.calculateLayout(state, 400f, 1000f, density)).isNull()
    }

    @Test
    fun calculateLayout_returnsNullOnNarrowCanvas() {
        val state = createState(lines(20, "val x = 0"))
        assertThat(renderer.calculateLayout(state, 60f, 1000f, density)).isNull()
    }

    @Test
    fun calculateLayout_mapsRowsAndThumbToVisualLineSpace() {
        val state = createState(lines(20, "val x = 0"))
        val layout = renderer.calculateLayout(state, canvasWidth = 400f, canvasHeight = 1000f, density)

        assertThat(layout).isNotNull()
        layout ?: return

        // 右边界 = 画布宽 - 滚动条占位(2+5) - 间隙 1 - 小地图宽 40 = 352。
        assertThat(layout.left).isEqualTo(352f)
        assertThat(layout.right).isEqualTo(392f)
        // 20 行 * 基准行高(40 * 0.25 = 10) = 200px，画布有 1000px，不需要压缩。
        assertThat(layout.rowHeightPx).isEqualTo(10f)
        assertThat(layout.height).isEqualTo(200f)
        // thumb = 视口可容纳的行数 * 行高 = 10 行 * 10px。
        assertThat(layout.thumbHeightPx).isEqualTo(100f)
        assertThat(layout.thumbTopPx).isEqualTo(0f)

        assertThat(layout.visualLineAt(Offset(layout.left, 0f))).isEqualTo(0)
        assertThat(layout.visualLineAt(Offset(layout.left, 100f))).isEqualTo(10)
        // 超出末行的触摸点钳到最后一行。
        assertThat(layout.visualLineAt(Offset(layout.left, 199f))).isEqualTo(19)

        assertThat(layout.contains(Offset(351f, 0f))).isFalse()
        assertThat(layout.contains(Offset(352f, 0f))).isTrue()
        assertThat(layout.contains(Offset(392f, 0f))).isTrue()
        assertThat(layout.contains(Offset(393f, 0f))).isFalse()
    }

    @Test
    fun thumbFollowsScrollAndClampsAtBottom() {
        val state = createState(lines(20, "val x = 0"))
        state.scrollBy(80f)
        var layout = renderer.calculateLayout(state, 400f, 1000f, density)
        assertThat(layout?.thumbTopPx).isEqualTo(20f)

        // maxScrollPx = 800 - 400 + 200 = 600；滑到底时 thumbTop 原始值 150，超出
        // height(200) - thumbHeight(100) = 100，钳制后底边正好对齐小地图底边。
        state.scrollBy(10_000f)
        layout = renderer.calculateLayout(state, 400f, 1000f, density)
        assertThat(state.scrollOffsetPx).isEqualTo(600f)
        assertThat(layout).isNotNull()
        layout ?: return
        assertThat(layout.thumbTopPx).isEqualTo(100f)
        assertThat(layout.thumbTopPx + layout.thumbHeightPx).isEqualTo(layout.height)
    }

    @Test
    fun calculateLayout_compressesRowsWhenDocumentExceedsCanvasHeight() {
        val state = createState(lines(20, "val x = 0"))
        val layout = renderer.calculateLayout(state, canvasWidth = 400f, canvasHeight = 100f, density)

        assertThat(layout).isNotNull()
        layout ?: return

        assertThat(layout.rowHeightPx).isEqualTo(5f)
        assertThat(layout.height).isEqualTo(100f)
        assertThat(layout.rowCount * layout.rowHeightPx).isLessThan(100f + 0.001f)
    }

    @Test
    fun calculateLayout_thumbClampsToMinHeight() {
        // 视口只有一行高，thumb 按比例只有 10px，但不得低于 24dp 的最小高度。
        val state = createState(lines(20, "val x = 0"), viewportHeightPx = 40f)
        val layout = renderer.calculateLayout(state, 400f, 1000f, density)
        assertThat(layout?.thumbHeightPx).isEqualTo(24f)
    }

    @Test
    fun paintRows_requestsEveryVisualLineWithoutWordWrap() {
        val highlighter = RecordingHighlighter()
        val state = createState(lines(8, "val x = 0")).apply { this.highlighter = highlighter }
        val layout = renderer.calculateLayout(state, 400f, 1000f, density)!!

        paintIntoPicture(layout, state)

        assertThat(state.visualLineCount()).isEqualTo(8)
        assertThat(highlighter.requestedLines).hasSize(8)
    }

    @Test
    fun paintRows_skipsWrappedContinuationRows() {
        // 视口 300px / 字符宽 10px = 每行 30 列；60 个字符的行折成 2 个视觉行。
        val highlighter = RecordingHighlighter()
        val state = createState(
            text = lines(4, "x".repeat(60)),
            wordWrap = true
        ).apply { this.highlighter = highlighter }
        val layout = renderer.calculateLayout(state, 400f, 1000f, density)!!

        paintIntoPicture(layout, state)

        assertThat(state.visualLineCount()).isEqualTo(8)
        // 折行延续行不重复取同一文档行的 token，所以只查 4 次。
        assertThat(highlighter.requestedLines).hasSize(4)
    }

    @Test
    fun paintRows_samplesLongDocumentsWithinRowBudget() {
        val highlighter = RecordingHighlighter()
        val state = createState(lines(10_000, "val x = 0")).apply { this.highlighter = highlighter }
        val layout = renderer.calculateLayout(state, 400f, 1000f, density)!!

        paintIntoPicture(layout, state)

        assertThat(highlighter.requestedLines.size)
            .isAtMost(EditorMinimapRenderer.MAX_HIGHLIGHT_ROWS)
    }

    @Test
    fun paintRows_withoutHighlighter_stillDrawsDocumentShape() {
        val state = createState(lines(20, "val x = 0"))
        val layout = renderer.calculateLayout(state, 400f, 1000f, density)!!
        val colorScheme = EditorColorScheme.builtinDark()
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colorScheme.gutterBackground.toArgb())

        renderer.paintRows(canvas, layout, state, colorScheme)

        assertThat(bitmap.getPixel(layout.left.toInt(), 1))
            .isNotEqualTo(colorScheme.gutterBackground.toArgb())
    }

    private fun paintIntoPicture(layout: MinimapLayout, state: EditorState) {
        val colorScheme = EditorColorScheme.builtinDark()
        val picture = Picture()
        val canvas = picture.beginRecording(400, 400)
        renderer.paintRows(canvas, layout, state, colorScheme)
        renderer.paintThumb(canvas, layout, colorScheme)
        picture.endRecording()
    }

    private class RecordingHighlighter : SyntaxHighlighter {
        val requestedLines = mutableListOf<Int>()

        override fun highlight(text: String, visibleRange: IntRange): List<HighlightSpan> = emptyList()

        override fun getLineSegments(line: Int): List<HighlightLineSegment> {
            requestedLines += line
            return listOf(HighlightLineSegment(startColumn = 0, endColumn = 2, type = HighlightType.KEYWORD))
        }
    }
}
