package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wuxianggujun.tinaide.core.config.EditorThemeColorKey
import com.wuxianggujun.tinaide.core.editorapi.SemanticTokenModifier
import com.wuxianggujun.tinaide.core.editorapi.SemanticTokenType
import com.wuxianggujun.tinaide.core.treesitter.HighlightType
import java.util.Locale

/**
 * TinaEditor 配色方案（编辑器 UI + 语法高亮）。
 *
 * 设计目标：
 * - 运行时可切换（Prefs / 插件主题）
 * - UI 渲染层与语法高亮共用同一套颜色来源，避免“主题切换后局部没更新”
 */
data class EditorColorScheme(
    val background: Color,
    val foreground: Color,
    val lineNumberBackground: Color,
    val gutterBackground: Color,
    val gutterDivider: Color,
    val lineNumberForeground: Color,
    val lineNumberForegroundActive: Color,
    val selectionBackground: Color,
    val currentLineBackground: Color,
    val cursor: Color,
    val selectionHandle: Color,
    val scrollbarTrack: Color,
    val scrollbarThumb: Color,
    val scrollbarThumbHover: Color,
    // gutter/marker
    val breakpoint: Color,
    val bookmark: Color,
    val gutterDiagnostic: Color,
    // git gutter 色条（相对 HEAD 的逐行改动）
    val gitAdded: Color,
    val gitModified: Color,
    val gitDeleted: Color,
    val foldIconExpanded: Color,
    val foldIconCollapsed: Color,
    val foldIconWarning: Color,
    val foldPlaceholderForeground: Color = Color(0xFFA0A0A0),
    val foldPlaceholderBackground: Color = Color(0x20808080),
    // diagnostics
    val diagnosticError: Color,
    val diagnosticWarning: Color,
    val diagnosticInfo: Color,
    val diagnosticHint: Color,
    val rainbowBracketColors: List<Color> = DEFAULT_RAINBOW_BRACKET_COLORS,
    val bracketPairGuide: Color = Color(0x33808080),
    val bracketPairGuideActive: Color = Color(0x66808080),
    val whitespace: Color = Color(0x40808080),
    val syntax: EditorSyntaxColors,
    val findMatchBackground: Color = Color(0x557DAA20),
    val findMatchActiveBackground: Color = Color(0x99D99A20)
) {
    companion object {
        private val DEFAULT_RAINBOW_BRACKET_COLORS = listOf(
            Color(0xFFE6B422), // gold
            Color(0xFFDA70D6), // orchid
            Color(0xFF179FFF), // cornflower blue
            Color(0xFF00C9A7), // medium spring green
            Color(0xFFFF6B6B), // light coral
            Color(0xFFC0A0FF) // light purple
        )

        fun builtinGray(): EditorColorScheme {
            val foreground = Color(0xFFE6E8EB)
            val warning = Color(0xFFE6A700)
            val error = Color(0xFFD84A4A)
            return EditorColorScheme(
                background = Color(0xFF202124),
                foreground = foreground,
                lineNumberBackground = Color(0xFF2A2C31),
                gutterBackground = Color(0xFF25272C),
                gutterDivider = Color(0xFF3A3E45),
                lineNumberForeground = Color(0xFF9AA0A6),
                lineNumberForegroundActive = foreground,
                selectionBackground = Color(0x664A90D9),
                currentLineBackground = Color(0x1A3A5C8F),
                cursor = foreground,
                selectionHandle = Color(0xFF5DA3FF),
                scrollbarTrack = Color(0x55677182),
                scrollbarThumb = Color(0xB3E7EDF7),
                scrollbarThumbHover = Color(0xDDE7EDF7),
                breakpoint = error,
                bookmark = Color(0xFF2E7D32),
                gutterDiagnostic = warning,
                gitAdded = Color(0xFF4CAF50),
                gitModified = Color(0xFFFFB300),
                gitDeleted = Color(0xFFEF5350),
                foldIconExpanded = Color(0xFF7A7A7A),
                foldIconCollapsed = Color(0xFFB0B0B0),
                foldIconWarning = warning,
                diagnosticError = error,
                diagnosticWarning = warning,
                diagnosticInfo = Color(0xFF4AA3FF),
                diagnosticHint = Color(0xFF6FA8DC),
                syntax = EditorSyntaxColors(
                    defaultText = foreground,
                    keyword = Color(0xFF569CD6),
                    function = Color(0xFFDCDCAA),
                    // 普通标识符默认跟正文同色，避免在 C/C++ 里把用户变量刷得比关键字更抢眼。
                    variable = foreground,
                    property = foreground,
                    type = Color(0xFF4EC9B0),
                    string = Color(0xFFCE9178),
                    number = Color(0xFFB5CEA8),
                    comment = Color(0xFF6A9955),
                    operator = Color(0xFFD4D4D4),
                    punctuation = Color(0xFFD4D4D4),
                    constant = Color(0xFFD7BA7D),
                    builtin = Color(0xFF4EC9B0)
                )
            )
        }

        fun builtinDark(): EditorColorScheme {
            val foreground = Color(0xFFE6E8EB)
            return EditorColorScheme(
                background = Color(0xFF1E1E1E),
                foreground = foreground,
                lineNumberBackground = Color(0xFF252526),
                gutterBackground = Color(0xFF252526),
                gutterDivider = Color(0xFF2D2D2D),
                lineNumberForeground = Color(0xFF8E8E8E),
                lineNumberForegroundActive = foreground,
                selectionBackground = Color(0x66397FF5),
                currentLineBackground = Color(0x1AFFFFFF),
                cursor = foreground,
                selectionHandle = Color(0xFF5DA3FF),
                scrollbarTrack = Color(0x552D2D2D),
                scrollbarThumb = Color(0xB3C5C5C5),
                scrollbarThumbHover = Color(0xDDC5C5C5),
                breakpoint = builtinGray().breakpoint,
                bookmark = builtinGray().bookmark,
                gutterDiagnostic = builtinGray().gutterDiagnostic,
                gitAdded = builtinGray().gitAdded,
                gitModified = builtinGray().gitModified,
                gitDeleted = builtinGray().gitDeleted,
                foldIconExpanded = builtinGray().foldIconExpanded,
                foldIconCollapsed = builtinGray().foldIconCollapsed,
                foldIconWarning = builtinGray().foldIconWarning,
                diagnosticError = builtinGray().diagnosticError,
                diagnosticWarning = builtinGray().diagnosticWarning,
                diagnosticInfo = builtinGray().diagnosticInfo,
                diagnosticHint = builtinGray().diagnosticHint,
                syntax = builtinGray().syntax.copy(
                    defaultText = foreground,
                    variable = foreground,
                    property = foreground
                )
            )
        }

        fun builtinLight(): EditorColorScheme {
            val foreground = Color(0xFF1F1F1F)
            val warning = Color(0xFFE6A700)
            val error = Color(0xFFD84A4A)
            return EditorColorScheme(
                background = Color(0xFFFFFFFF),
                foreground = foreground,
                lineNumberBackground = Color(0xFFF3F3F3),
                gutterBackground = Color(0xFFF3F3F3),
                gutterDivider = Color(0xFFE0E0E0),
                lineNumberForeground = Color(0xFF6B6B6B),
                lineNumberForegroundActive = foreground,
                selectionBackground = Color(0x66397FF5),
                currentLineBackground = Color(0x0F000000),
                cursor = Color(0xFF000000),
                selectionHandle = Color(0xFF2F74FF),
                scrollbarTrack = Color(0x33000000),
                scrollbarThumb = Color(0x66000000),
                scrollbarThumbHover = Color(0x99000000),
                breakpoint = error,
                bookmark = Color(0xFF2E7D32),
                gutterDiagnostic = warning,
                gitAdded = Color(0xFF2E7D32),
                gitModified = Color(0xFFEF6C00),
                gitDeleted = Color(0xFFC62828),
                foldIconExpanded = Color(0xFF7A7A7A),
                foldIconCollapsed = Color(0xFFB0B0B0),
                foldIconWarning = warning,
                diagnosticError = error,
                diagnosticWarning = warning,
                diagnosticInfo = Color(0xFF1E6BD6),
                diagnosticHint = Color(0xFF1E6BD6),
                syntax = EditorSyntaxColors(
                    defaultText = foreground,
                    keyword = Color(0xFF0000AA),
                    function = Color(0xFF795E26),
                    variable = foreground,
                    property = foreground,
                    type = Color(0xFF267F99),
                    string = Color(0xFFA31515),
                    number = Color(0xFF098658),
                    comment = Color(0xFF008000),
                    operator = foreground,
                    punctuation = foreground,
                    constant = Color(0xFF800000),
                    builtin = Color(0xFF267F99)
                )
            )
        }

        /** Only canonical EditorThemeColorKey wire names are accepted. */
        fun fromThemeColors(
            colors: Map<String, String>,
            fallback: EditorColorScheme = builtinGray()
        ): EditorColorScheme {
            fun color(key: EditorThemeColorKey, default: Color): Color =
                parseColorOrNull(colors[key.wireName]) ?: default

            return fallback.copy(
                background = color(EditorThemeColorKey.EDITOR_BACKGROUND, fallback.background),
                foreground = color(EditorThemeColorKey.EDITOR_FOREGROUND, fallback.foreground),
                lineNumberBackground = color(EditorThemeColorKey.EDITOR_LINE_NUMBER_BACKGROUND, fallback.lineNumberBackground),
                gutterBackground = color(EditorThemeColorKey.GUTTER_BACKGROUND, fallback.gutterBackground),
                gutterDivider = color(EditorThemeColorKey.GUTTER_DIVIDER, fallback.gutterDivider),
                lineNumberForeground = color(EditorThemeColorKey.EDITOR_LINE_NUMBER, fallback.lineNumberForeground),
                lineNumberForegroundActive = color(EditorThemeColorKey.EDITOR_LINE_NUMBER_ACTIVE, fallback.lineNumberForegroundActive),
                selectionBackground = color(EditorThemeColorKey.EDITOR_SELECTION, fallback.selectionBackground),
                currentLineBackground = color(EditorThemeColorKey.EDITOR_CURSOR_LINE, fallback.currentLineBackground),
                cursor = color(EditorThemeColorKey.EDITOR_CURSOR, fallback.cursor),
                selectionHandle = color(EditorThemeColorKey.EDITOR_SELECTION_HANDLE, fallback.selectionHandle),
                scrollbarTrack = color(EditorThemeColorKey.SCROLLBAR_TRACK, fallback.scrollbarTrack),
                scrollbarThumb = color(EditorThemeColorKey.SCROLLBAR_THUMB, fallback.scrollbarThumb),
                scrollbarThumbHover = color(EditorThemeColorKey.SCROLLBAR_THUMB_HOVER, fallback.scrollbarThumbHover),
                breakpoint = color(EditorThemeColorKey.GUTTER_BREAKPOINT, fallback.breakpoint),
                bookmark = color(EditorThemeColorKey.GUTTER_BOOKMARK, fallback.bookmark),
                gutterDiagnostic = color(EditorThemeColorKey.GUTTER_DIAGNOSTIC, fallback.gutterDiagnostic),
                gitAdded = color(EditorThemeColorKey.GUTTER_GIT_ADDED, fallback.gitAdded),
                gitModified = color(EditorThemeColorKey.GUTTER_GIT_MODIFIED, fallback.gitModified),
                gitDeleted = color(EditorThemeColorKey.GUTTER_GIT_DELETED, fallback.gitDeleted),
                foldIconExpanded = color(EditorThemeColorKey.FOLDING_ICON_EXPANDED, fallback.foldIconExpanded),
                foldIconCollapsed = color(EditorThemeColorKey.FOLDING_ICON_COLLAPSED, fallback.foldIconCollapsed),
                foldIconWarning = color(EditorThemeColorKey.FOLDING_ICON_WARNING, fallback.foldIconWarning),
                foldPlaceholderForeground = color(EditorThemeColorKey.FOLDING_PLACEHOLDER_FOREGROUND, fallback.foldPlaceholderForeground),
                foldPlaceholderBackground = color(EditorThemeColorKey.FOLDING_PLACEHOLDER_BACKGROUND, fallback.foldPlaceholderBackground),
                diagnosticError = color(EditorThemeColorKey.DIAGNOSTIC_ERROR, fallback.diagnosticError),
                diagnosticWarning = color(EditorThemeColorKey.DIAGNOSTIC_WARNING, fallback.diagnosticWarning),
                diagnosticInfo = color(EditorThemeColorKey.DIAGNOSTIC_INFO, fallback.diagnosticInfo),
                diagnosticHint = color(EditorThemeColorKey.DIAGNOSTIC_HINT, fallback.diagnosticHint),
                bracketPairGuide = color(EditorThemeColorKey.BRACKET_PAIR_GUIDE, fallback.bracketPairGuide),
                bracketPairGuideActive = color(EditorThemeColorKey.BRACKET_PAIR_GUIDE_ACTIVE, fallback.bracketPairGuideActive),
                whitespace = color(EditorThemeColorKey.EDITOR_WHITESPACE, fallback.whitespace),
                findMatchBackground = color(EditorThemeColorKey.EDITOR_FIND_MATCH, fallback.findMatchBackground),
                findMatchActiveBackground = color(EditorThemeColorKey.EDITOR_FIND_MATCH_ACTIVE, fallback.findMatchActiveBackground),
                rainbowBracketColors = (0 until 6).map { index ->
                    parseColorOrNull(colors["rainbowBrackets.$index"])
                        ?: fallback.rainbowBracketColors.getOrElse(index) { DEFAULT_RAINBOW_BRACKET_COLORS[index] }
                },
                syntax = fallback.syntax.copy(
                    defaultText = color(EditorThemeColorKey.EDITOR_FOREGROUND, fallback.foreground),
                    keyword = color(EditorThemeColorKey.SYNTAX_KEYWORD, fallback.syntax.keyword),
                    function = color(EditorThemeColorKey.SYNTAX_FUNCTION, fallback.syntax.function),
                    variable = color(EditorThemeColorKey.SYNTAX_VARIABLE, fallback.syntax.variable),
                    property = color(EditorThemeColorKey.SYNTAX_PROPERTY, fallback.syntax.property),
                    type = color(EditorThemeColorKey.SYNTAX_TYPE, fallback.syntax.type),
                    string = color(EditorThemeColorKey.SYNTAX_STRING, fallback.syntax.string),
                    number = color(EditorThemeColorKey.SYNTAX_NUMBER, fallback.syntax.number),
                    comment = color(EditorThemeColorKey.SYNTAX_COMMENT, fallback.syntax.comment),
                    operator = color(EditorThemeColorKey.SYNTAX_OPERATOR, fallback.syntax.operator),
                    punctuation = color(EditorThemeColorKey.SYNTAX_PUNCTUATION, fallback.syntax.punctuation),
                    constant = color(EditorThemeColorKey.SYNTAX_CONSTANT, fallback.syntax.constant),
                    builtin = color(EditorThemeColorKey.SYNTAX_BUILTIN, fallback.syntax.builtin),
                    deprecated = color(EditorThemeColorKey.SYNTAX_DEPRECATED, fallback.syntax.deprecated),
                )
            )
        }

        private fun parseColorOrNull(value: String?): Color? {
            val raw = value?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val normalized = when {
                raw.startsWith("0x", ignoreCase = true) -> "#${raw.drop(2)}"
                else -> raw
            }
            return runCatching {
                Color(android.graphics.Color.parseColor(normalized))
            }.getOrNull()
        }
    }

    fun toThemeColors(): Map<String, String> = buildMap {
        put(EditorThemeColorKey.EDITOR_BACKGROUND.wireName, background.toThemeHex())
        put(EditorThemeColorKey.EDITOR_FOREGROUND.wireName, foreground.toThemeHex())
        put(EditorThemeColorKey.EDITOR_LINE_NUMBER_BACKGROUND.wireName, lineNumberBackground.toThemeHex())
        put(EditorThemeColorKey.GUTTER_BACKGROUND.wireName, gutterBackground.toThemeHex())
        put(EditorThemeColorKey.GUTTER_DIVIDER.wireName, gutterDivider.toThemeHex())
        put(EditorThemeColorKey.EDITOR_LINE_NUMBER.wireName, lineNumberForeground.toThemeHex())
        put(EditorThemeColorKey.EDITOR_LINE_NUMBER_ACTIVE.wireName, lineNumberForegroundActive.toThemeHex())
        put(EditorThemeColorKey.EDITOR_SELECTION.wireName, selectionBackground.toThemeHex())
        put(EditorThemeColorKey.EDITOR_CURSOR_LINE.wireName, currentLineBackground.toThemeHex())
        put(EditorThemeColorKey.EDITOR_CURSOR.wireName, cursor.toThemeHex())
        put(EditorThemeColorKey.EDITOR_SELECTION_HANDLE.wireName, selectionHandle.toThemeHex())
        put(EditorThemeColorKey.SCROLLBAR_TRACK.wireName, scrollbarTrack.toThemeHex())
        put(EditorThemeColorKey.SCROLLBAR_THUMB.wireName, scrollbarThumb.toThemeHex())
        put(EditorThemeColorKey.SCROLLBAR_THUMB_HOVER.wireName, scrollbarThumbHover.toThemeHex())
        put(EditorThemeColorKey.GUTTER_BREAKPOINT.wireName, breakpoint.toThemeHex())
        put(EditorThemeColorKey.GUTTER_BOOKMARK.wireName, bookmark.toThemeHex())
        put(EditorThemeColorKey.GUTTER_DIAGNOSTIC.wireName, gutterDiagnostic.toThemeHex())
        put(EditorThemeColorKey.GUTTER_GIT_ADDED.wireName, gitAdded.toThemeHex())
        put(EditorThemeColorKey.GUTTER_GIT_MODIFIED.wireName, gitModified.toThemeHex())
        put(EditorThemeColorKey.GUTTER_GIT_DELETED.wireName, gitDeleted.toThemeHex())
        put(EditorThemeColorKey.FOLDING_ICON_EXPANDED.wireName, foldIconExpanded.toThemeHex())
        put(EditorThemeColorKey.FOLDING_ICON_COLLAPSED.wireName, foldIconCollapsed.toThemeHex())
        put(EditorThemeColorKey.FOLDING_ICON_WARNING.wireName, foldIconWarning.toThemeHex())
        put(EditorThemeColorKey.FOLDING_PLACEHOLDER_FOREGROUND.wireName, foldPlaceholderForeground.toThemeHex())
        put(EditorThemeColorKey.FOLDING_PLACEHOLDER_BACKGROUND.wireName, foldPlaceholderBackground.toThemeHex())
        put(EditorThemeColorKey.DIAGNOSTIC_ERROR.wireName, diagnosticError.toThemeHex())
        put(EditorThemeColorKey.DIAGNOSTIC_WARNING.wireName, diagnosticWarning.toThemeHex())
        put(EditorThemeColorKey.DIAGNOSTIC_INFO.wireName, diagnosticInfo.toThemeHex())
        put(EditorThemeColorKey.DIAGNOSTIC_HINT.wireName, diagnosticHint.toThemeHex())
        put(EditorThemeColorKey.BRACKET_PAIR_GUIDE.wireName, bracketPairGuide.toThemeHex())
        put(EditorThemeColorKey.BRACKET_PAIR_GUIDE_ACTIVE.wireName, bracketPairGuideActive.toThemeHex())
        put(EditorThemeColorKey.EDITOR_WHITESPACE.wireName, whitespace.toThemeHex())
        put(EditorThemeColorKey.EDITOR_FIND_MATCH.wireName, findMatchBackground.toThemeHex())
        put(EditorThemeColorKey.EDITOR_FIND_MATCH_ACTIVE.wireName, findMatchActiveBackground.toThemeHex())
        rainbowBracketColors.take(6).forEachIndexed { index, color ->
            put("rainbowBrackets.$index", color.toThemeHex())
        }
        put(EditorThemeColorKey.SYNTAX_KEYWORD.wireName, syntax.keyword.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_FUNCTION.wireName, syntax.function.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_VARIABLE.wireName, syntax.variable.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_PROPERTY.wireName, syntax.property.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_TYPE.wireName, syntax.type.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_STRING.wireName, syntax.string.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_NUMBER.wireName, syntax.number.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_COMMENT.wireName, syntax.comment.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_OPERATOR.wireName, syntax.operator.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_PUNCTUATION.wireName, syntax.punctuation.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_CONSTANT.wireName, syntax.constant.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_BUILTIN.wireName, syntax.builtin.toThemeHex())
        put(EditorThemeColorKey.SYNTAX_DEPRECATED.wireName, syntax.deprecated.toThemeHex())
    }
}

private fun Color.toThemeHex(): String = String.format(Locale.ROOT, "#%08X", toArgb())

data class EditorSyntaxColors(
    val defaultText: Color,
    val keyword: Color,
    val function: Color,
    val variable: Color,
    val property: Color = variable,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val operator: Color,
    val punctuation: Color,
    val constant: Color = type, // 命名常量：cmake VERSION/SHARED，Kotlin SCREAMING_CASE
    val builtin: Color = type, // 内置标识符：内置函数/类型/变量
    val deprecated: Color = Color(0xFFA0A0A0)
) {
    fun colorOf(highlightType: HighlightType): Color = when (highlightType) {
        HighlightType.KEYWORD -> keyword
        HighlightType.FUNCTION -> function
        HighlightType.VARIABLE -> variable
        HighlightType.PROPERTY -> property
        HighlightType.TYPE -> type
        HighlightType.STRING -> string
        HighlightType.NUMBER -> number
        HighlightType.COMMENT -> comment
        HighlightType.OPERATOR -> operator
        HighlightType.PUNCTUATION -> punctuation
        HighlightType.CONSTANT -> constant
        HighlightType.BUILTIN -> builtin
        HighlightType.DEFAULT -> defaultText
    }

    fun colorOfSemantic(
        tokenType: SemanticTokenType,
        tokenModifiers: Set<SemanticTokenModifier>
    ): Color {
        if (SemanticTokenModifier.DEPRECATED in tokenModifiers) {
            return deprecated
        }

        val mappedType = when (tokenType) {
            SemanticTokenType.NAMESPACE,
            SemanticTokenType.TYPE,
            SemanticTokenType.CLASS,
            SemanticTokenType.ENUM,
            SemanticTokenType.INTERFACE,
            SemanticTokenType.STRUCT,
            SemanticTokenType.TYPE_PARAMETER -> HighlightType.TYPE

            SemanticTokenType.PARAMETER,
            SemanticTokenType.VARIABLE,
            SemanticTokenType.PROPERTY,
            SemanticTokenType.EVENT -> HighlightType.PROPERTY

            SemanticTokenType.ENUM_MEMBER -> HighlightType.CONSTANT

            SemanticTokenType.FUNCTION,
            SemanticTokenType.METHOD,
            SemanticTokenType.MACRO -> HighlightType.FUNCTION

            SemanticTokenType.KEYWORD,
            SemanticTokenType.MODIFIER -> HighlightType.KEYWORD

            SemanticTokenType.COMMENT -> HighlightType.COMMENT
            SemanticTokenType.STRING,
            SemanticTokenType.REGEXP -> HighlightType.STRING

            SemanticTokenType.NUMBER -> HighlightType.NUMBER
            SemanticTokenType.OPERATOR -> HighlightType.OPERATOR
            SemanticTokenType.CUSTOM -> HighlightType.DEFAULT
        }
        return colorOf(mappedType)
    }
}
