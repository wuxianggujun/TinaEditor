package com.wuxianggujun.tinaide.core.editorview

data class EditorInlayHint(
    val line: Int,
    val column: Int,
    val label: String,
    val kind: EditorInlayHintKind = EditorInlayHintKind.OTHER,
    val paddingLeft: Boolean = false,
    val paddingRight: Boolean = false,
)

enum class EditorInlayHintKind {
    PARAMETER,
    TYPE,
    OTHER,
}

data class EditorCompletionItem(
    val label: String,
    val detail: String? = null,
    val insertText: String = label,
    val kind: EditorCompletionKind = EditorCompletionKind.TEXT,
    val filterText: String? = null,
    val textEdit: EditorCompletionTextEdit? = null,
    val additionalTextEdits: List<EditorCompletionTextEdit> = emptyList(),
    val snippetText: String? = null,
    val isLsp: Boolean = false
)

data class EditorCompletionTextEdit(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val newText: String
)

enum class EditorCompletionKind {
    TEXT,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    FIELD,
    VARIABLE,
    CLASS,
    INTERFACE,
    MODULE,
    PROPERTY,
    UNIT,
    VALUE,
    ENUM,
    KEYWORD,
    SNIPPET,
    COLOR,
    FILE,
    REFERENCE,
    FOLDER,
    ENUM_MEMBER,
    CONSTANT,
    STRUCT,
    EVENT,
    OPERATOR,
    TYPE_PARAMETER
}

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT
}

data class EditorDiagnostic(
    val line: Int,
    val startColumn: Int,
    val endColumn: Int,
    val message: String,
    val severity: DiagnosticSeverity
)

data class GutterDecoration(
    val breakpoint: Boolean = false,
    val bookmark: Boolean = false,
    val hasDiagnostic: Boolean = false,
    val foldable: Boolean = false
)

/** 行号栏 git 修改指示色条的类型（相对 HEAD）。宿主计算后写入 [EditorState.gitLineChanges]。 */
enum class EditorGitLineChangeType { ADDED, MODIFIED, DELETED }
