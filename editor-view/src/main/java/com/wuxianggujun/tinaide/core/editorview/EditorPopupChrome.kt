package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal val editorPopupCornerRadius = 8.dp
internal val editorPopupBorderWidth = 1.dp
internal val editorPopupElevation = 8.dp
internal val editorPopupSectionInset = 8.dp
internal val editorPopupActionPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
internal val editorPopupCompactActionPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)

internal data class EditorPopupColors(
    val containerColor: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val selectedSurfaceColor: Color,
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val matchTextColor: Color,
    val progressTrackColor: Color
)

@Composable
internal fun rememberEditorPopupColors(
    colorScheme: EditorColorScheme
): EditorPopupColors = remember(colorScheme) { resolveEditorPopupColors(colorScheme) }

/** Host content slots (including Markdown) and Material controls share the editor's palette. */
@Composable
internal fun EditorPopupTheme(colors: EditorPopupColors, content: @Composable () -> Unit) {
    val materialColors = remember(colors) { resolveEditorPopupMaterialColors(colors) }
    MaterialTheme(colorScheme = materialColors) {
        CompositionLocalProvider(LocalContentColor provides colors.primaryTextColor, content = content)
    }
}

internal fun resolveEditorPopupMaterialColors(colors: EditorPopupColors): ColorScheme {
    val base = if (colors.containerColor.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
    val onAccent = readablePopupColor(colors.primaryTextColor, colors.accentColor)
    val error = readablePopupColor(base.error, colors.containerColor, colors.selectedSurfaceColor)
    return base.copy(
        primary = colors.accentColor, onPrimary = onAccent,
        primaryContainer = colors.selectedSurfaceColor, onPrimaryContainer = colors.primaryTextColor,
        secondary = colors.accentColor, onSecondary = onAccent,
        secondaryContainer = colors.selectedSurfaceColor, onSecondaryContainer = colors.primaryTextColor,
        tertiary = colors.accentColor, onTertiary = onAccent,
        tertiaryContainer = colors.selectedSurfaceColor, onTertiaryContainer = colors.primaryTextColor,
        background = colors.containerColor, onBackground = colors.primaryTextColor,
        surface = colors.containerColor, onSurface = colors.primaryTextColor,
        surfaceVariant = colors.selectedSurfaceColor, onSurfaceVariant = colors.secondaryTextColor,
        surfaceDim = colors.containerColor, surfaceBright = colors.containerColor,
        surfaceContainerLowest = colors.containerColor, surfaceContainerLow = colors.containerColor,
        surfaceContainer = colors.containerColor, surfaceContainerHigh = colors.selectedSurfaceColor,
        surfaceContainerHighest = colors.selectedSurfaceColor,
        outline = readablePopupColor(colors.borderColor, colors.containerColor, minimumContrast = 3f),
        outlineVariant = colors.dividerColor,
        error = error, onError = readablePopupColor(base.onError, error),
        surfaceTint = colors.accentColor
    )
}

@Composable
internal fun EditorPopupSurface(
    modifier: Modifier = Modifier,
    colors: EditorPopupColors,
    content: @Composable () -> Unit
) {
    EditorPopupTheme(colors) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(editorPopupCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = colors.containerColor, contentColor = colors.primaryTextColor
            ),
            border = BorderStroke(editorPopupBorderWidth, colors.borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = editorPopupElevation)
        ) {
            Box(modifier = Modifier.background(colors.containerColor)) {
                content()
            }
        }
    }
}

@Composable
internal fun EditorPopupScaffold(
    colors: EditorPopupColors,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit
) {
    EditorPopupSurface(
        modifier = modifier,
        colors = colors
    ) {
        Column(
            modifier = contentModifier.background(colors.containerColor),
            content = content
        )
    }
}

@Composable
internal fun EditorPopupLoadingBar(
    colors: EditorPopupColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape),
            color = colors.accentColor,
            trackColor = colors.progressTrackColor,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
internal fun EditorPopupDivider(
    colors: EditorPopupColors,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = editorPopupSectionInset),
        color = colors.dividerColor
    )
}

@Composable
internal fun EditorPopupActionButton(
    onClick: () -> Unit,
    colors: EditorPopupColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = colors.primaryTextColor,
    disabledContentColor: Color = colors.secondaryTextColor.copy(alpha = 0.45f),
    contentPadding: PaddingValues = editorPopupActionPadding,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = disabledContentColor
        ),
        contentPadding = contentPadding,
        content = content
    )
}

internal fun resolveEditorPopupColors(scheme: EditorColorScheme): EditorPopupColors {
    val darkTheme = scheme.background.perceivedBrightness() < 0.5f
    val container = mixColor(
        start = scheme.background,
        end = scheme.foreground,
        ratio = if (darkTheme) 0.055f else 0.018f
    ).copy(alpha = 1f)
    val border = mixColor(
        start = scheme.gutterDivider,
        end = scheme.foreground,
        ratio = if (darkTheme) 0.22f else 0.08f
    ).copy(alpha = if (darkTheme) 0.92f else 0.6f)
    val divider = mixColor(
        start = scheme.gutterDivider,
        end = scheme.foreground,
        ratio = if (darkTheme) 0.3f else 0.14f
    ).copy(alpha = if (darkTheme) 0.72f else 0.45f)
    // Opaque, gently raised surfaces keep code blocks and selected rows readable even
    // when a plugin uses translucent or extreme colors for the document selection.
    val foreground = readablePopupColor(scheme.foreground, container)
    val selected = mixColor(container, foreground, 0.08f).copy(alpha = 1f).let { candidate ->
        if (popupContrastRatio(foreground, candidate) >= 4.5f) candidate else
            mixColor(container, if (foreground.luminance() > container.luminance()) Color.Black else Color.White, 0.08f)
    }
    val secondaryText = mixColor(
        start = scheme.lineNumberForeground,
        end = scheme.foreground,
        ratio = if (darkTheme) 0.18f else 0.1f
    ).copy(alpha = if (darkTheme) 0.95f else 0.82f)
    val matchedText = mixColor(
        start = scheme.syntax.keyword,
        end = scheme.selectionHandle,
        ratio = 0.25f
    )
    val progressTrack = mixColor(
        start = container,
        end = scheme.foreground,
        ratio = if (darkTheme) 0.12f else 0.05f
    )
    return EditorPopupColors(
        containerColor = container,
        borderColor = border,
        dividerColor = divider,
        selectedSurfaceColor = selected,
        primaryTextColor = readablePopupColor(foreground, container, selected),
        secondaryTextColor = readablePopupColor(secondaryText, container, selected),
        accentColor = readablePopupColor(scheme.selectionHandle, container, selected),
        matchTextColor = readablePopupColor(matchedText, container, selected),
        progressTrackColor = progressTrack
    )
}

/** WCAG contrast after alpha compositing; used only when resolving a theme, never per frame. */
internal fun popupContrastRatio(foreground: Color, background: Color): Float {
    val surface = background.copy(alpha = 1f)
    val text = foreground.compositeOver(surface).luminance()
    val backdrop = surface.luminance()
    return (maxOf(text, backdrop) + 0.05f) / (minOf(text, backdrop) + 0.05f)
}

private fun readablePopupColor(
    preferred: Color,
    background: Color,
    selectedBackground: Color = background,
    minimumContrast: Float = 4.5f
): Color {
    fun contrast(color: Color) = minOf(
        popupContrastRatio(color, background), popupContrastRatio(color, selectedBackground)
    )
    if (contrast(preferred) >= minimumContrast) return preferred
    val fallback = if (contrast(Color.White) > contrast(Color.Black)) Color.White else Color.Black
    // Retain the theme hue as far as contrast allows.
    for (step in 1..20) {
        val candidate = lerp(preferred, fallback, step / 20f)
        if (contrast(candidate) >= minimumContrast) return candidate
    }
    return fallback
}

private fun mixColor(start: Color, end: Color, ratio: Float): Color = lerp(start, end, ratio.coerceIn(0f, 1f))

private fun Color.perceivedBrightness(): Float = (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
