package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TinaCodeViewerTest {
    @get:Rule val compose = createComposeRule()
    private val visible = mutableStateOf(true)
    private lateinit var state: EditorState

    @After
    fun closeDocumentAfterItsRendererHasDetached() {
        if (!::state.isInitialized) return
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        state.close()
        state.textBuffer.close()
    }

    @Test
    fun codeSurfaceScrollsWithoutCreatingAnEditableOrFocusableInputHost() {
        val code = (1..30).joinToString("\n") { "const int long_identifier_${it}_" + "x".repeat(100) + " = 1;" }
        state = EditorState(RopeTextBuffer(code), config = EditorConfig(wordWrap = false, codeFolding = false))
        compose.setContent {
            if (visible.value) TinaCodeViewer(state, Modifier.size(240.dp, 100.dp))
        }
        val viewer = compose.onNodeWithTag("tina-code-viewer")
        val semantics = viewer.fetchSemanticsNode().config
        assertThat(semantics.contains(SemanticsActions.SetText)).isFalse()
        assertThat(semantics.contains(SemanticsActions.RequestFocus)).isFalse()
        assertThat(semantics[SemanticsProperties.Text].single().text).isEqualTo(code)
        viewer.performTouchInput { click() }
        viewer.performSemanticsAction(SemanticsActions.ScrollBy) { it(100f, 40f) }
        compose.runOnIdle {
            assertThat(state.scrollOffsetXPx).isGreaterThan(0f)
            assertThat(state.scrollOffsetPx).isGreaterThan(0f)
            assertThat(state.isFocused).isFalse()
            assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo(code)
        }
    }

    @Test
    fun externallyReplacedDocumentUpdatesItsReadOnlyAccessibleText() {
        state = EditorState(RopeTextBuffer("int value = 1;"), config = EditorConfig(wordWrap = false))
        compose.setContent {
            if (visible.value) TinaCodeViewer(state, Modifier.size(240.dp, 100.dp))
        }
        compose.runOnIdle { state.textBuffer.insert(state.textBuffer.length, "\nreturn value;") }
        val code = compose.onNodeWithTag("tina-code-viewer").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertThat(code).isEqualTo("int value = 1;\nreturn value;")
    }
}
