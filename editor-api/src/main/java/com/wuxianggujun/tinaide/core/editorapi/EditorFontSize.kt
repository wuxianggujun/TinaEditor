package com.wuxianggujun.tinaide.core.editorapi

/** Shared by editor gestures, host preferences and settings UI. Sizes retain their Float precision. */
object EditorFontSize {
    const val MIN_SP = 8f
    const val MAX_SP = 48f
    const val DEFAULT_SP = 14f

    fun normalize(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_SP, MAX_SP) else DEFAULT_SP
}
