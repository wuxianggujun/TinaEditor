package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.TransformScope
import androidx.compose.foundation.gestures.TransformableState

/** Settle the preview in the gesture transaction, not in a later composition. */
internal class EditorTransformableState(
    private val delegate: TransformableState,
    private val onTransformFinished: () -> Unit
) : TransformableState by delegate {
    override suspend fun transform(
        transformPriority: MutatePriority,
        block: suspend TransformScope.() -> Unit
    ) {
        delegate.transform(transformPriority) {
            try {
                block()
            } finally {
                // Run while the delegate still owns its mutation lock. A cancelled or
                // superseded transform must settle before another one can start.
                onTransformFinished()
            }
        }
    }
}
