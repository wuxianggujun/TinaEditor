package io.github.tinaide.editor.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wuxianggujun.tinaide.core.editorview.EditorState
import com.wuxianggujun.tinaide.core.editorview.EditorCustomRenderer
import com.wuxianggujun.tinaide.core.editorview.EditorRenderExtension
import com.wuxianggujun.tinaide.core.editorview.EditorRenderLayer
import com.wuxianggujun.tinaide.core.editorview.OffsetRange
import com.wuxianggujun.tinaide.core.editorview.TinaEditor
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer

@Composable
fun EditorExample() {
    val buffer = remember { RopeTextBuffer("Hello, editor!\n") }
    val editorState = remember(buffer) {
        EditorState(textBuffer = buffer).apply {
            find.search("editor")
            find.show()
            renderExtensions = listOf(
                EditorRenderExtension(
                    layer = EditorRenderLayer.Foreground,
                    renderer = EditorCustomRenderer { scope, frame ->
                        frame.rangeRectangles(OffsetRange(0, 5)).forEach { rectangle ->
                            scope.drawLine(
                                color = frame.colorScheme.cursor,
                                start = rectangle.bottomLeft,
                                end = rectangle.bottomRight,
                                strokeWidth = 2f
                            )
                        }
                    }
                )
            )
        }
    }
    TinaEditor(
        state = editorState,
        modifier = Modifier.fillMaxSize(),
        onToggleLineComment = { editorState.toggleLineComment("//") }
    )
}
