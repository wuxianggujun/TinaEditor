package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Typeface
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TinaEditorKeyboardIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closingFind_restoresSelectionAndInputHostFocusAfterRemovingTheField() {
        val state = createState("abcdefghij")
        var session: TinaEditorSession? = null
        lateinit var oldConnection: EditorInputConnection
        composeRule.setContent {
            val resolvedSession = rememberTinaEditorSession(state)
            session = resolvedSession
            TinaEditorScaffold(resolvedSession)
        }
        composeRule.runOnIdle {
            val controller = checkNotNull(session).interactionController
            controller.requestEditorFocusAndKeyboard()
            val host = checkNotNull(controller.inputHostView)
            oldConnection = host.onCreateInputConnection(EditorInfo()) as EditorInputConnection
            state.selectRange(10, 0)
            state.find.show()
        }
        composeRule.onNodeWithTag("editor_find_query").assertIsFocused()
        composeRule.runOnIdle {
            assertThat(oldConnection.setSelection(0, 7)).isFalse()
            assertThat(state.selectionRange).isEqualTo(OffsetRange(10, 0))
        }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        composeRule.runOnIdle {
            val host = checkNotNull(session?.interactionController?.inputHostView)
            assertThat(host.isFocused).isTrue()
            assertThat(state.selectionRange).isEqualTo(OffsetRange(10, 0))
        }
    }

    @Test
    fun tinaEditorScaffold_shouldRouteCtrlSlashThroughHardwareInputHost() {
        val state = createState("value")
        var session: TinaEditorSession? = null
        var consumed = false

        composeRule.setContent {
            val resolvedSession = rememberTinaEditorSession(state)
            session = resolvedSession
            TinaEditorScaffold(
                session = resolvedSession,
                onToggleLineComment = { state.toggleLineComment("//") }
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val host = checkNotNull(session?.interactionController?.inputHostView)
            val keyEventHandler = checkNotNull(host.keyEventHandler)
            consumed = keyEventHandler(
                AndroidKeyEvent(
                    0L,
                    0L,
                    AndroidKeyEvent.ACTION_DOWN,
                    AndroidKeyEvent.KEYCODE_SLASH,
                    0,
                    AndroidKeyEvent.META_CTRL_ON
                )
            )
        }
        composeRule.waitForIdle()

        assertThat(consumed).isTrue()
        assertThat(state.textBuffer.toString()).isEqualTo("// value")
    }

    private fun createState(text: String): EditorState = EditorState(
        textBuffer = RopeTextBuffer(text),
        config = EditorConfig(
            wordWrap = false,
            codeFolding = false,
            tabSize = 4
        )
    ).apply {
        typeface = Typeface.MONOSPACE
        fontSizeSp = 14f
    }
}
