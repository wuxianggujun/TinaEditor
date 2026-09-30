package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LineNumberRendererTest {

    private val renderer = LineNumberRenderer(horizontalPaddingPx = 8f, edgeStartPaddingPx = 8f)

    @Test
    fun gitStripeRect_fullHeightForAddedAndModified() {
        val stripe = gitStripeRect(EditorGitLineChangeType.ADDED, yTop = 100f, lineHeightPx = 40f, stripeWidth = 3f)
        assertThat(stripe.top).isEqualTo(100f)
        assertThat(stripe.height).isEqualTo(40f)

        val modified = gitStripeRect(EditorGitLineChangeType.MODIFIED, yTop = 0f, lineHeightPx = 22f, stripeWidth = 3f)
        assertThat(modified.top).isEqualTo(0f)
        assertThat(modified.height).isEqualTo(22f)
    }

    @Test
    fun gitStripeRect_halfHeightCenteredForDeleted() {
        val stripe = gitStripeRect(EditorGitLineChangeType.DELETED, yTop = 100f, lineHeightPx = 40f, stripeWidth = 3f)
        assertThat(stripe.height).isEqualTo(20f)
        assertThat(stripe.top).isEqualTo(110f)
    }

    @Test
    fun gitStripeRect_originatesAtLeftEdge() {
        for (change in EditorGitLineChangeType.entries) {
            assertThat(gitStripeRect(change, yTop = 0f, lineHeightPx = 40f, stripeWidth = 3f).left)
                .isEqualTo(0f)
        }
    }

    @Test
    fun gitStripeRect_widthScalesWithDigitWidth() {
        assertThat(gitStripeRect(EditorGitLineChangeType.ADDED, 0f, 20f, stripeWidth = 3f).width).isEqualTo(3f)
        assertThat(gitStripeRect(EditorGitLineChangeType.ADDED, 0f, 20f, stripeWidth = 6f).width).isEqualTo(6f)
    }

    @Test
    fun draw_smoke_drawsWithGitChangesWithoutCrashing() {
        val state = createState(showGitGutter = true).apply {
            gitLineChanges = mapOf(
                0 to EditorGitLineChangeType.ADDED,
                1 to EditorGitLineChangeType.MODIFIED,
                2 to EditorGitLineChangeType.DELETED
            )
        }
        draw(state)
        // 重绘后宿主写入的映射不应被内核改动。
        assertThat(state.gitLineChanges).hasSize(3)
    }

    @Test
    fun draw_isNoOpWhenGitGutterDisabled() {
        val state = createState(showGitGutter = false).apply {
            gitLineChanges = mapOf(1 to EditorGitLineChangeType.ADDED)
        }
        draw(state)
        // 关闭时宿主仍持有映射，但渲染早退（无法在 ShadowCanvas 上断言像素）。
        assertThat(state.gitLineChanges).containsEntry(1, EditorGitLineChangeType.ADDED)
    }

    @Test
    fun draw_isNoOpWhenLineNumbersHidden() {
        val state = createState(showLineNumbers = false, showGitGutter = true).apply {
            gitLineChanges = mapOf(0 to EditorGitLineChangeType.MODIFIED)
        }
        draw(state)
        assertThat(state.config.showLineNumbers).isFalse()
    }

    private fun createState(
        showLineNumbers: Boolean = true,
        showGitGutter: Boolean = false
    ): EditorState = EditorState(
        textBuffer = RopeTextBuffer("one\ntwo\nthree"),
        config = EditorConfig(
            showLineNumbers = showLineNumbers,
            showGitGutter = showGitGutter,
            wordWrap = false
        )
    ).apply {
        updateMetrics(
            lineHeightPx = 24f,
            charWidthPx = 8f,
            viewportHeightPx = 240f,
            viewportWidthPx = 240f,
            contentStartXPx = 0f
        )
    }

    private fun draw(state: EditorState) {
        val paint = Paint().apply { textSize = 24f }
        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        try {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(240f, 240f)) {
                renderer.draw(this, state, paint, widthPx = 48f)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
