package com.wuxianggujun.tinaide.core.editorapi

data class SignatureHelpResult(
    val signatures: List<String>,
    val activeSignature: Int,
    val activeParameter: Int
)
