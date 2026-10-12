package com.wuxianggujun.tinaide.core.editorview

internal fun EditorState.seedVisibleHover(
    markdown: String = "hover docs"
) {
    assistance.publishHoverVisible(markdown)
}
