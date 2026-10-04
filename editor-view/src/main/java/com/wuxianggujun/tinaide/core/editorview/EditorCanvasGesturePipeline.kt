package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerId

internal class EditorCanvasGesturePipeline(
    private val gestureCoordinator: EditorGestureCoordinator,
    private val scrollGestureCoordinator: EditorScrollGestureCoordinator,
    private val mouseHoverCoordinator: EditorMouseHoverCoordinator,
    private val isScrollbarDragActive: () -> Boolean,
    private val isHandleDragging: () -> Boolean,
    private val onTransformGestureFocusChanged: (TransformGestureFocusSnapshot) -> Unit
) {
    private var isCtrlPressedForLatestPointerEvent = false
    private var isAltPressedForLatestPointerEvent = false

    suspend fun AwaitPointerEventScope.observePointerStream() {
        val pressedChanges = ArrayList<PointerInputChange>(4)
        var rectanglePointer: PointerId? = null
        var rectangleAnchor = Offset.Zero
        var rectangleMoved = false
        while (true) {
            // 在 Initial pass 先采样焦点，确保 transformable(Main pass)拿到的是同帧焦点。
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            isCtrlPressedForLatestPointerEvent = event.keyboardModifiers.isCtrlPressed
            isAltPressedForLatestPointerEvent = event.keyboardModifiers.isAltPressed
            val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse }
            if (rectanglePointer == null && mouse != null && mouse.pressed && !mouse.previousPressed &&
                event.buttons.isPrimaryPressed && isAltPressedForLatestPointerEvent && !mouse.isConsumed &&
                !isScrollbarDragActive() && !isHandleDragging() && gestureCoordinator.canStartRectangle(mouse.position)) {
                rectanglePointer = mouse.id
                rectangleAnchor = mouse.position
                rectangleMoved = false
            }
            val rectangleChange = event.changes.firstOrNull { it.id == rectanglePointer }
            if (rectangleChange != null) {
                rectangleChange.consume()
                if ((rectangleChange.position - rectangleAnchor).getDistance() >= viewConfiguration.touchSlop) rectangleMoved = true
                if (rectangleMoved) gestureCoordinator.onRectangleSelection(rectangleAnchor, rectangleChange.position)
                if (!rectangleChange.pressed) {
                    if (!rectangleMoved) gestureCoordinator.onTap(rectangleChange.position, isAltPressed = true)
                    rectanglePointer = null
                }
            }
            pressedChanges.clear()
            event.changes.forEach { change ->
                if (change.pressed) {
                    pressedChanges += change
                }
            }
            val pointerCount = pressedChanges.size
            if (pointerCount == 0) {
                val hoverChange = event.changes.firstOrNull()
                if (
                    hoverChange?.type == PointerType.Mouse &&
                    (event.type == PointerEventType.Move || event.type == PointerEventType.Enter)
                ) {
                    mouseHoverCoordinator.onMove(hoverChange.position)
                } else if (event.type == PointerEventType.Exit || event.type == PointerEventType.Scroll) {
                    mouseHoverCoordinator.cancelAndDismiss()
                }
            } else {
                mouseHoverCoordinator.cancelAndDismiss()
            }
            gestureCoordinator.onPointerCountChanged(pointerCount)
            onTransformGestureFocusChanged(
                scrollGestureCoordinator.onPointerStreamUpdated(
                    pressedChanges = pressedChanges,
                    scrollbarDragActive = isScrollbarDragActive(),
                    isHandleDragging = isHandleDragging()
                )
            )
        }
    }

    fun onTap(position: Offset) {
        gestureCoordinator.onTap(
            position = position,
            isCtrlPressed = isCtrlPressedForLatestPointerEvent,
            isAltPressed = isAltPressedForLatestPointerEvent
        )
    }

    fun onLongPress(position: Offset) {
        gestureCoordinator.onLongPress(position)
    }

    fun onSecondaryClick(position: Offset) {
        gestureCoordinator.onSecondaryClick(position)
    }

    fun onCursorDragStart(position: Offset) {
        gestureCoordinator.onCursorDragStart(position)
    }

    fun onCursorDrag(position: Offset): Boolean = gestureCoordinator.onCursorDrag(position)

    fun onCursorDragEnd() {
        gestureCoordinator.onCursorDragEnd()
    }

    fun onCursorDragCancel() {
        gestureCoordinator.onCursorDragCancel()
    }

    fun onSelectionDragStart(position: Offset) {
        gestureCoordinator.onSelectionDragStart(position)
    }

    fun onSelectionDrag(position: Offset): Boolean = gestureCoordinator.onSelectionDrag(position)

    fun onSelectionDragEnd() {
        gestureCoordinator.onSelectionDragEnd()
    }

    fun onSelectionDragCancel() {
        gestureCoordinator.onSelectionDragCancel()
    }
}
