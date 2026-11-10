package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorPopupChromeTest {

    @Test
    fun popupChromeMetrics_shouldRemainAlignedWithPopupShellSpec() {
        assertThat(editorPopupCornerRadius).isEqualTo(8.dp)
        assertThat(editorPopupBorderWidth).isEqualTo(1.dp)
        assertThat(editorPopupElevation).isEqualTo(8.dp)
    }

    @Test
    fun resolveEditorPopupColors_shouldKeepDarkPopupSurfaceCloseToBackground() {
        val scheme = EditorColorScheme.builtinDark()

        val colors = resolveEditorPopupColors(scheme)

        assertThat(colors.primaryTextColor).isEqualTo(scheme.foreground)
        assertReadable(colors)
        assertThat(colors.containerColor.perceivedBrightness())
            .isGreaterThan(scheme.background.perceivedBrightness())
        assertThat(colors.selectedSurfaceColor).isNotEqualTo(colors.containerColor)
        assertThat(colors.borderColor.alpha).isGreaterThan(colors.dividerColor.alpha)
    }

    @Test
    fun resolveEditorPopupColors_shouldKeepLightPopupSurfaceSubtle() {
        val scheme = EditorColorScheme.builtinLight()

        val colors = resolveEditorPopupColors(scheme)

        assertThat(colors.primaryTextColor).isEqualTo(scheme.foreground)
        assertReadable(colors)
        assertThat(colors.containerColor.perceivedBrightness())
            .isLessThan(scheme.background.perceivedBrightness())
        assertThat(colors.progressTrackColor).isNotEqualTo(colors.containerColor)
        assertThat(colors.borderColor.alpha).isGreaterThan(colors.dividerColor.alpha)
    }

    @Test
    fun pluginColorsWithLowContrast_produceReadableTextControlsAndMarkdownSurfaces() {
        for (background in listOf(Color.Black, Color.White, Color(0xFF777777), Color(0xFF202124))) {
            val scheme = EditorColorScheme.builtinDark().copy(
                background = background, foreground = background,
                lineNumberForeground = background.copy(alpha = 0.2f),
                selectionHandle = background, selectionBackground = Color.Transparent
            )
            val colors = resolveEditorPopupColors(scheme)
            assertReadable(colors)
            val material = resolveEditorPopupMaterialColors(colors)
            for ((text, surface) in listOf(
                material.onSurface to material.surface,
                material.onSurface to material.surfaceContainerHighest,
                material.onSurfaceVariant to material.surfaceContainerHigh,
                material.primary to material.surface,
                material.onPrimaryContainer to material.primaryContainer,
                material.error to material.surface
            )) {
                assertThat(popupContrastRatio(text, surface)).isAtLeast(4.5f)
            }
        }
    }

    private fun assertReadable(colors: EditorPopupColors) {
        for (surface in listOf(colors.containerColor, colors.selectedSurfaceColor)) {
            for (text in listOf(colors.primaryTextColor, colors.secondaryTextColor, colors.accentColor, colors.matchTextColor)) {
                assertThat(popupContrastRatio(text, surface)).isAtLeast(4.5f)
            }
        }
    }

    private fun androidx.compose.ui.graphics.Color.perceivedBrightness(): Float = (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
}
