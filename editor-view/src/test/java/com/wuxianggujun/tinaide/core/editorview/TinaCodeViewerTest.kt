package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TinaCodeViewerTest {
    @get:Rule val compose = createComposeRule()
    private val visible = mutableStateOf(true)
    private lateinit var state: EditorState
    private lateinit var hostView: android.view.View

    private fun drawViewer() {
        compose.runOnIdle {
            val bitmap = android.graphics.Bitmap.createBitmap(hostView.width, hostView.height, android.graphics.Bitmap.Config.ARGB_8888)
            try { hostView.draw(android.graphics.Canvas(bitmap)) } finally { bitmap.recycle() }
            assertThat(state.lineHeightPx).isGreaterThan(1f)
        }
    }

    @After
    fun closeDocumentAfterItsRendererHasDetached() {
        if (!::state.isInitialized) return
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        state.close()
        state.textBuffer.close()
    }

    @Test
    fun selectableViewerLongPressCopiesOnlyTheSelectionWithoutEditingOrAnImeHost() {
        state = EditorState(RopeTextBuffer("alpha beta\n你好😀"), config = EditorConfig(wordWrap = false, codeFolding = false))
        compose.setContent {
            hostView = androidx.compose.ui.platform.LocalView.current
            if (visible.value) TinaCodeViewer(state, Modifier.size(320.dp, 140.dp), selectable = true)
        }
        drawViewer()
        val viewer = compose.onNodeWithTag("tina-code-viewer")
        viewer.performTouchInput { longClick(androidx.compose.ui.geometry.Offset(state.contentStartXPx + state.charWidthPx * 2, state.lineHeightPx * .5f)) }
        compose.runOnIdle {
            assertThat(state.selectedText()).isEqualTo("alpha")
            assertThat(state.isFocused).isFalse()
        }
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_CUT_ACTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_PASTE_ACTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_MORE_ACTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_COPY_ACTION_TAG).performClick()
        compose.runOnIdle {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertThat(clipboard.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("alpha")
            assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("alpha beta\n你好😀")
        }
        val semantics = viewer.fetchSemanticsNode().config
        assertThat(semantics.contains(SemanticsActions.SetText)).isFalse()
        assertThat(semantics.contains(SemanticsActions.RequestFocus)).isFalse()
    }

    @Test
    fun selectionHandlesExtendAcrossLinesWithoutOpeningAnInputHost() {
        val traces = mutableListOf<String>()
        val tree = object : timber.log.Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (tag == "CodeViewerSelection") traces.add(message)
            }
        }
        timber.log.Timber.plant(tree)
        // Robolectric has no PixelCopy Surface for Android's platform magnifier.
        state = EditorState(RopeTextBuffer("alpha beta\nmiddle line\ngamma delta"), config = EditorConfig(
            wordWrap = false, codeFolding = false, selectionMagnifierEnabled = false,
        ))
        var fontPixels = 0f
        compose.setContent {
            hostView = androidx.compose.ui.platform.LocalView.current
            fontPixels = with(androidx.compose.ui.platform.LocalDensity.current) { state.fontSizeSp.sp.toPx() }
            if (visible.value) TinaCodeViewer(state, Modifier.size(320.dp, 180.dp), selectable = true)
        }
        drawViewer()
        val viewer = compose.onNodeWithTag("tina-code-viewer")
        viewer.performTouchInput { longClick(androidx.compose.ui.geometry.Offset(state.contentStartXPx + state.charWidthPx, state.lineHeightPx * .5f)) }
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_COPY_ACTION_TAG).performClick()
        var handle = androidx.compose.ui.geometry.Offset.Zero
        compose.runOnIdle {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = fontPixels; typeface = state.typeface }
            val layout = resolveSelectionHandleLayout(state, state.contentStartXPx, paint, EditorLineLayoutCache(), state.textBuffer::getLine)!!
            handle = layout.endCenter.copy(x = layout.endCenter.x - state.scrollOffsetXPx)
        }
        viewer.performTouchInput { swipe(handle, handle + androidx.compose.ui.geometry.Offset(0f, state.lineHeightPx * 2f), 400) }
        timber.log.Timber.uproot(tree)
        compose.runOnIdle {
            com.google.common.truth.Truth.assertWithMessage("handle=%s height=%s scroll=%s traces=%s", handle, state.lineHeightPx, state.scrollOffsetPx, traces)
                .that(state.selectedText()).isEqualTo("alpha beta\nmiddle line\ngamma")
            assertThat(state.isFocused).isFalse()
        }
        compose.onNodeWithTag(SELECTION_CONTEXT_MENU_COPY_ACTION_TAG).assertExists()
    }

    @Test
    fun ordinaryDragStillScrollsTheSelectableViewer() {
        state = EditorState(RopeTextBuffer((1..100).joinToString("\n") { "row$it" }))
        compose.setContent { if (visible.value) TinaCodeViewer(state, Modifier.size(320.dp, 180.dp), selectable = true) }
        compose.onNodeWithTag("tina-code-viewer").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertThat(state.scrollOffsetPx).isGreaterThan(0f)
            assertThat(state.selectionRange).isNull()
        }
    }

    @Test
    fun selectableViewerSupportsAccessibleSelectionAndFullUnicodeCopy() {
        val text = "第一行😀\nsecond line"
        state = EditorState(RopeTextBuffer(text))
        compose.setContent { if (visible.value) TinaCodeViewer(state, Modifier.size(320.dp, 140.dp), selectable = true) }
        val viewer = compose.onNodeWithTag("tina-code-viewer")
        viewer.performSemanticsAction(SemanticsActions.SetSelection) { assertThat(it(0, text.length, false)).isTrue() }
        viewer.performSemanticsAction(SemanticsActions.CopyText) { assertThat(it()).isTrue() }
        compose.runOnIdle { assertThat(state.selectedText()).isEqualTo(text) }
        viewer.performSemanticsAction(SemanticsActions.SetSelection) { assertThat(it(-1, text.length + 1, false)).isFalse() }
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
    fun displayOnlyLabelsRemainAccessibleWithoutConsumingBodyColumnsOrDispatchingTaps() {
        state = EditorState(RopeTextBuffer("info\nwarning"), config = EditorConfig(
            wordWrap = false, codeFolding = false, gutterMode = EditorGutterMode.LABELS,
        ))
        state.gutterDecorations[0] = GutterDecoration(label = GutterLineLabel('I'))
        state.gutterDecorations[1] = GutterDecoration(label = GutterLineLabel('W'))
        var callbacks = 0
        state.onLineNumberTap = { callbacks++ }
        state.onGutterTap = { callbacks++ }
        compose.setContent {
            if (visible.value) TinaCodeViewer(state, Modifier.size(240.dp, 100.dp))
        }
        val viewer = compose.onNodeWithTag("tina-code-viewer")
        assertThat(viewer.fetchSemanticsNode().config[SemanticsProperties.Text].single().text).isEqualTo("[I] info\n[W] warning")
        viewer.performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, 5f)) }
        compose.runOnIdle {
            assertThat(callbacks).isEqualTo(0)
            assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("info\nwarning")
            state.gutterDecorations[1] = GutterDecoration(label = GutterLineLabel('E'))
        }
        assertThat(viewer.fetchSemanticsNode().config[SemanticsProperties.Text].single().text).isEqualTo("[I] info\n[E] warning")
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
