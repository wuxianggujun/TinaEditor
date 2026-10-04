package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.graphics.drawscope.DrawScope

internal object FindMatchHighlightRenderer {
    fun draw(scope: DrawScope, context: EditorRenderContext, find: EditorFindController) {
        if (!find.visible || context.visibleRows.isEmpty()) return
        val matches = find.matches
        val first = context.visibleRows.first().startOffset
        val last = context.visibleRows.last().endOffset
        var low = 0
        var high = matches.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (matches[middle].end < first) low = middle + 1 else high = middle
        }
        var index = low
        while (index < matches.size && matches[index].start <= last) {
            val color = if (index == find.activeIndex) context.colorScheme.findMatchActiveBackground
                else context.colorScheme.findMatchBackground
            context.rangeRectangles(matches[index]).forEach { rect ->
                scope.drawRect(color, rect.topLeft, rect.size)
            }
            index++
        }
    }
}
