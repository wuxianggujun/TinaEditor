package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import android.graphics.Typeface
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.ceil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 使用真实字形度量，避免测试 shadow 掩盖组合字符和代理项的合法零 advance。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28, 34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorLineLayoutMeasurementTest {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 24f
    }

    @Test
    fun prefixWidthMatchesWholeLineMeasurement() {
        val cache = EditorLineLayoutCache()
        val samples = listOf("ASCII text", "中文😀z", "e\u0301x", "👩‍💻 end", "مرحبا")

        samples.forEachIndexed { line, text ->
            val layout = cache.getPrefixLayout(line, text, 1L, paint, 4)

            // measureText 对整段宽度向上取整，prefix 则保留各字形的实际小数 advance。
            assertWithMessage("Whole-line width for %s", text)
                .that(ceil(layout.prefix.last())).isEqualTo(paint.measureText(text))
        }
    }

    @Test
    fun zeroAdvanceCodeUnitsDoNotAddFallbackGlyphWidths() {
        val text = "a😀e\u0301\u200Dz"
        val advances = FloatArray(text.length)
        paint.getTextWidths(text, advances)
        assertThat(advances.any { it == 0f }).isTrue()
        val cache = EditorLineLayoutCache()

        val layout = cache.getPrefixLayout(0, text, 1L, paint, 4)

        for (index in text.indices) {
            assertWithMessage("Advance at UTF-16 index %s", index)
                .that(layout.prefix[index + 1] - layout.prefix[index])
                .isWithin(0.01f).of(advances[index])
        }
        val midpoint = (layout.prefix[1] + layout.prefix[3]) / 2f
        assertThat(cache.xToColumn(layout, midpoint - 0.1f)).isEqualTo(1)
        assertThat(cache.xToColumn(layout, midpoint + 0.1f)).isEqualTo(3)
    }

    @Test
    fun shortLineAfterLongLinePreservesTabStopsAndCachedLayout() {
        val cache = EditorLineLayoutCache()
        cache.getPrefixLayout(0, "long line ".repeat(100), 1L, paint, 4)
        val text = "a\t中文😀"

        val layout = cache.getPrefixLayout(1, text, 1L, paint, 4)

        assertThat(layout.prefix).hasLength(text.length + 1)
        assertThat(layout.prefix[2] - layout.prefix[1])
            .isWithin(0.01f).of(paint.measureText(" ") * 3)
        assertThat(ceil(layout.prefix.last())).isEqualTo(
            paint.measureText("a中文😀") + paint.measureText(" ") * 3,
        )
        assertThat(cache.getPrefixLayout(1, text, 1L, paint, 4)).isSameInstanceAs(layout)
    }
}
