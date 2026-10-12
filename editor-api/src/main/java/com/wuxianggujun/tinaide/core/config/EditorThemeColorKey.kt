package com.wuxianggujun.tinaide.core.config

enum class EditorThemeColorGroup {
    EDITOR,
    SYNTAX,
    DIAGNOSTICS
}

/** Stable color keys shared by the editor and host theme adapters. */
enum class EditorThemeColorKey(
    val wireName: String,
    val group: EditorThemeColorGroup
) {
    EDITOR_LINE_NUMBER_BACKGROUND("editor.lineNumberBackground", EditorThemeColorGroup.EDITOR),
    EDITOR_SELECTION_HANDLE("editor.selectionHandle", EditorThemeColorGroup.EDITOR),
    SCROLLBAR_TRACK("scrollbar.track", EditorThemeColorGroup.EDITOR),
    SCROLLBAR_THUMB("scrollbar.thumb", EditorThemeColorGroup.EDITOR),
    SCROLLBAR_THUMB_HOVER("scrollbar.thumbHover", EditorThemeColorGroup.EDITOR),
    GUTTER_BREAKPOINT("gutter.breakpoint", EditorThemeColorGroup.EDITOR),
    GUTTER_BOOKMARK("gutter.bookmark", EditorThemeColorGroup.EDITOR),
    GUTTER_DIAGNOSTIC("gutter.diagnostic", EditorThemeColorGroup.EDITOR),
    GUTTER_GIT_ADDED("gutter.gitAdded", EditorThemeColorGroup.EDITOR),
    GUTTER_GIT_MODIFIED("gutter.gitModified", EditorThemeColorGroup.EDITOR),
    GUTTER_GIT_DELETED("gutter.gitDeleted", EditorThemeColorGroup.EDITOR),
    FOLDING_ICON_EXPANDED("folding.iconExpanded", EditorThemeColorGroup.EDITOR),
    FOLDING_ICON_COLLAPSED("folding.iconCollapsed", EditorThemeColorGroup.EDITOR),
    FOLDING_ICON_WARNING("folding.iconWarning", EditorThemeColorGroup.EDITOR),
    FOLDING_PLACEHOLDER_FOREGROUND("folding.placeholderForeground", EditorThemeColorGroup.EDITOR),
    FOLDING_PLACEHOLDER_BACKGROUND("folding.placeholderBackground", EditorThemeColorGroup.EDITOR),
    EDITOR_FIND_MATCH("editor.findMatch", EditorThemeColorGroup.EDITOR),
    EDITOR_FIND_MATCH_ACTIVE("editor.findMatchActive", EditorThemeColorGroup.EDITOR),
    EDITOR_BACKGROUND("editor.background", EditorThemeColorGroup.EDITOR),
    EDITOR_FOREGROUND("editor.foreground", EditorThemeColorGroup.EDITOR),
    EDITOR_SELECTION("editor.selection", EditorThemeColorGroup.EDITOR),
    EDITOR_CURSOR_LINE("editor.cursorLine", EditorThemeColorGroup.EDITOR),
    EDITOR_CURSOR("editor.cursor", EditorThemeColorGroup.EDITOR),
    EDITOR_LINE_NUMBER("editor.lineNumber", EditorThemeColorGroup.EDITOR),
    EDITOR_LINE_NUMBER_ACTIVE("editor.lineNumberActive", EditorThemeColorGroup.EDITOR),
    GUTTER_BACKGROUND("gutter.background", EditorThemeColorGroup.EDITOR),
    GUTTER_DIVIDER("gutter.divider", EditorThemeColorGroup.EDITOR),
    EDITOR_WHITESPACE("editor.whitespace", EditorThemeColorGroup.EDITOR),
    BRACKET_PAIR_GUIDE("editor.bracketPairGuide", EditorThemeColorGroup.EDITOR),
    BRACKET_PAIR_GUIDE_ACTIVE("editor.bracketPairGuideActive", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_1("rainbowBrackets.0", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_2("rainbowBrackets.1", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_3("rainbowBrackets.2", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_4("rainbowBrackets.3", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_5("rainbowBrackets.4", EditorThemeColorGroup.EDITOR),
    RAINBOW_BRACKET_6("rainbowBrackets.5", EditorThemeColorGroup.EDITOR),
    SYNTAX_KEYWORD("syntax.keyword", EditorThemeColorGroup.SYNTAX),
    SYNTAX_FUNCTION("syntax.function", EditorThemeColorGroup.SYNTAX),
    SYNTAX_VARIABLE("syntax.variable", EditorThemeColorGroup.SYNTAX),
    SYNTAX_PROPERTY("syntax.property", EditorThemeColorGroup.SYNTAX),
    SYNTAX_TYPE("syntax.type", EditorThemeColorGroup.SYNTAX),
    SYNTAX_STRING("syntax.string", EditorThemeColorGroup.SYNTAX),
    SYNTAX_NUMBER("syntax.number", EditorThemeColorGroup.SYNTAX),
    SYNTAX_COMMENT("syntax.comment", EditorThemeColorGroup.SYNTAX),
    SYNTAX_OPERATOR("syntax.operator", EditorThemeColorGroup.SYNTAX),
    SYNTAX_PUNCTUATION("syntax.punctuation", EditorThemeColorGroup.SYNTAX),
    SYNTAX_CONSTANT("syntax.constant", EditorThemeColorGroup.SYNTAX),
    SYNTAX_BUILTIN("syntax.builtin", EditorThemeColorGroup.SYNTAX),
    SYNTAX_DEPRECATED("syntax.deprecated", EditorThemeColorGroup.SYNTAX),
    DIAGNOSTIC_ERROR("diagnostic.error", EditorThemeColorGroup.DIAGNOSTICS),
    DIAGNOSTIC_WARNING("diagnostic.warning", EditorThemeColorGroup.DIAGNOSTICS),
    DIAGNOSTIC_INFO("diagnostic.info", EditorThemeColorGroup.DIAGNOSTICS),
    DIAGNOSTIC_HINT("diagnostic.hint", EditorThemeColorGroup.DIAGNOSTICS);

    companion object {
        private val byWireName = entries.associateBy(EditorThemeColorKey::wireName)

        fun fromWireName(wireName: String): EditorThemeColorKey? = byWireName[wireName]
    }
}
