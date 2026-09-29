package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.Density
import kotlin.coroutines.cancellation.CancellationException

internal data class ActiveMinimapDrag(
    val pointerId: PointerId
)

/**
 * 小地图的点击/拖动手势：点中哪一行就把那一行居中到视口，拖动时连续跳转。
 *
 * 手势结构与 [ScrollbarDragCoordinator] 一致（Initial 通道消费、命中即接管、
 * 240ms 基础手势抑制、结束时恢复滚动条可见性），差异只在命中区域与坐标映射。
 */
internal class EditorMinimapDragCoordinator(
    private val state: EditorState,
    private val minimapRenderer: EditorMinimapRenderer,
    private val scrollGestureCoordinator: EditorScrollGestureCoordinator,
    private val gestureHandler: EditorGestureHandler,
    private val onActiveDragChanged: (ActiveMinimapDrag?) -> Unit,
    private val onContextMenuVisibilityChanged: (Boolean) -> Unit,
    private val onTriggerScrollbarVisibility: (Boolean) -> Unit,
    private val cancelPendingCompletionRequest: () -> Unit,
    private val logEditorTouch: (String, Boolean) -> Unit
) {
    suspend fun AwaitPointerEventScope.runDragLoop(
        canvasWidthPxProvider: () -> Float,
        canvasHeightPxProvider: () -> Float,
        density: Density
    ) {
        try {
            while (true) {
                val down = awaitFirstPointerDown(pass = PointerEventPass.Initial)
                val layout = minimapRenderer.calculateLayout(
                    state = state,
                    canvasWidth = canvasWidthPxProvider(),
                    canvasHeight = canvasHeightPxProvider(),
                    density = density
                ) ?: continue
                if (!layout.contains(down.position)) continue

                // 命中小地图后立即接管，否则 40dp 的热区会被文本手势抢走。
                gestureHandler.suppressBasicGestures(durationMs = 240L)
                logEditorTouch(
                    "minimap pressCandidate pointer=(${down.position.x.toInt()},${down.position.y.toInt()})",
                    false
                )

                val drag = ActiveMinimapDrag(pointerId = down.id)
                onActiveDragChanged(drag)
                scrollGestureCoordinator.onScrollbarDragStarted(ScrollbarAxis.Vertical)
                onContextMenuVisibilityChanged(false)
                onTriggerScrollbarVisibility(true)
                cancelPendingCompletionRequest()
                down.consume()

                scrollToPosition(layout, down.position)
                var dragFinished = false
                try {
                    var dragging = true
                    while (dragging) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == drag.pointerId }
                        if (change == null || !change.pressed) {
                            dragging = false
                            continue
                        }
                        scrollToPosition(layout, change.position)
                        change.consume()
                    }
                    dragFinished = true
                } finally {
                    if (!dragFinished) {
                        logEditorTouch("minimap dragAbort reason=cancelled", true)
                    }
                    onActiveDragChanged(null)
                    scrollGestureCoordinator.onScrollbarDragFinished()
                    onTriggerScrollbarVisibility(false)
                }
            }
        } catch (e: CancellationException) {
            logEditorTouch("minimap pointerInputCancel reason=cancelled", true)
            throw e
        }
    }

    private fun scrollToPosition(layout: MinimapLayout, position: Offset) {
        state.scrollToVisualLine(layout.visualLineAt(position), center = true)
    }
}
