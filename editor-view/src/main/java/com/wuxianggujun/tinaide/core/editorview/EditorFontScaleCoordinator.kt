package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.runtime.snapshots.Snapshot
import com.wuxianggujun.tinaide.core.editorapi.EditorFontSize

internal class EditorFontScaleCoordinator(
    private val state: EditorState
) {
    fun apply(rawSizeSp: Float, commitGeometry: () -> Unit = {}) {
        if (!rawSizeSp.isFinite()) return
        val targetSize = EditorFontSize.normalize(rawSizeSp)
        if (targetSize == state.fontSizeSp) return

        // Do not publish a font change while the viewport still contains the old metrics.
        // The host may synchronously echo the preference or inspect the committed geometry.
        Snapshot.withMutableSnapshot {
            state.fontSizeSp = targetSize
            commitGeometry()
        }
        state.runtimeOptions.onFontSizeChanged(targetSize)
    }
}
