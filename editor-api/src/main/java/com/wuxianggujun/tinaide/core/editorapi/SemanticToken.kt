package com.wuxianggujun.tinaide.core.editorapi

data class SemanticToken(
    val line: Int,
    val startColumn: Int,
    val length: Int,
    val tokenType: SemanticTokenType,
    val tokenModifiers: Set<SemanticTokenModifier> = emptySet(),
)

enum class SemanticTokenType {
    NAMESPACE,
    TYPE,
    CLASS,
    ENUM,
    INTERFACE,
    STRUCT,
    TYPE_PARAMETER,
    PARAMETER,
    VARIABLE,
    PROPERTY,
    ENUM_MEMBER,
    EVENT,
    FUNCTION,
    METHOD,
    MACRO,
    KEYWORD,
    MODIFIER,
    COMMENT,
    STRING,
    NUMBER,
    REGEXP,
    OPERATOR,
    CUSTOM;

    companion object {
        fun fromWireName(value: String): SemanticTokenType = when (
            value.trim().lowercase().replace('-', '_')
        ) {
            "namespace" -> NAMESPACE
            "type" -> TYPE
            "class" -> CLASS
            "enum" -> ENUM
            "interface" -> INTERFACE
            "struct" -> STRUCT
            "typeparameter", "type_parameter" -> TYPE_PARAMETER
            "parameter" -> PARAMETER
            "variable" -> VARIABLE
            "property" -> PROPERTY
            "enummember", "enum_member" -> ENUM_MEMBER
            "event" -> EVENT
            "function" -> FUNCTION
            "method" -> METHOD
            "macro" -> MACRO
            "keyword" -> KEYWORD
            "modifier" -> MODIFIER
            "comment" -> COMMENT
            "string" -> STRING
            "number" -> NUMBER
            "regexp", "regex" -> REGEXP
            "operator" -> OPERATOR
            else -> CUSTOM
        }
    }
}

enum class SemanticTokenModifier {
    DECLARATION,
    DEFINITION,
    READONLY,
    STATIC,
    DEPRECATED,
    ABSTRACT,
    ASYNC,
    MODIFICATION,
    DOCUMENTATION,
    DEFAULT_LIBRARY;

    companion object {
        fun fromWireName(value: String): SemanticTokenModifier? = when (
            value.trim().lowercase().replace('-', '_')
        ) {
            "declaration" -> DECLARATION
            "definition" -> DEFINITION
            "readonly", "read_only" -> READONLY
            "static" -> STATIC
            "deprecated" -> DEPRECATED
            "abstract" -> ABSTRACT
            "async" -> ASYNC
            "modification" -> MODIFICATION
            "documentation" -> DOCUMENTATION
            "defaultlibrary", "default_library" -> DEFAULT_LIBRARY
            else -> null
        }
    }
}
