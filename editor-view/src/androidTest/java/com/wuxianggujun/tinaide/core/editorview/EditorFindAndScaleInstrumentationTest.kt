package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Bitmap
import android.graphics.Typeface
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.treesitter.TreeSitterFoldingProvider.FoldRegion
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real device gestures, IME handoff and rendered palette regressions. */
@RunWith(AndroidJUnit4::class)
class EditorFindAndScaleInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val buffer = RopeTextBuffer("abcdefghij\n" + List(160) { "int value_$it = 123; // " + "long_content ".repeat(12) }.joinToString("\n"))
    private val state = EditorState(buffer, config = EditorConfig(wordWrap = false, fontSizeSp = 16f, codeFolding = true)).apply {
        typeface = Typeface.MONOSPACE
    }
    private var mounted by mutableStateOf(true)
    private var darkHost by mutableStateOf(false)
    private lateinit var session: TinaEditorSession
    private var hoverColor: Color? = null
    private lateinit var inputModeManager: InputModeManager
    private var editorWidth by mutableStateOf(1000.dp)

    @Before fun configureOrientation() {
        val landscape = InstrumentationRegistry.getArguments().getString("editorOrientation") == "landscape"
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val expected = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        composeRule.waitUntil(10_000) { composeRule.activity.resources.configuration.orientation == expected }
        composeRule.activityRule.scenario.onActivity { activity ->
            // Match MainActivity: adjustPan moves the entire window offscreen when
            // a lower field gains focus, including controls outside the scroll area.
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun mount() {
        composeRule.setContent {
            if (mounted) MaterialTheme(colorScheme = if (darkHost) darkColorScheme() else lightColorScheme()) {
                inputModeManager = LocalInputModeManager.current
                session = rememberTinaEditorSession(state)
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TinaEditorScaffold(session, Modifier.widthIn(max = editorWidth).fillMaxSize().testTag("regression_editor"), hoverContent = { text, modifier, _, _ ->
                        hoverColor = MaterialTheme.colorScheme.onSurface
                        Text(text, modifier, color = MaterialTheme.colorScheme.onSurface)
                    })
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After fun tearDown() {
        if (::session.isInitialized) {
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
        }
        buffer.close()
    }

    @Test fun longPressFindClose_preservesTenCharactersAndReturnsImeOwnership() {
        mount()
        composeRule.onNodeWithTag("regression_editor").performTouchInput {
            longClick(Offset(state.contentStartXPx + state.charWidthPx * 3.5f, state.lineHeightPx * 0.5f))
        }
        composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_SELECT_ALL_ACTION_TAG).assertIsDisplayed()
        val original = state.selectionSet
        assertEquals("abcdefghij", state.selectedText())
        screenshot("selection-primary-actions")
        composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_MORE_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("editor_selection_context_menu_action_Find").performClick()
        composeRule.onNodeWithTag("editor_find_query").assertIsFocused()
        composeRule.waitUntil(5_000) { state.find.isCurrent }
        screenshot("find-from-long-press")
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("abc")
        composeRule.waitUntil(5_000) { state.find.isCurrent }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        composeRule.runOnIdle {
            assertEquals(original, state.selectionSet)
            val host = checkNotNull(session.interactionController.inputHostView)
            assertTrue(host.isFocused)
            val connection = host.onCreateInputConnection(EditorInfo()) as EditorInputConnection
            assertTrue(connection.commitText("replacement", 1))
            assertTrue(buffer.toString().startsWith("replacement\n"))
            state.undo()
            assertTrue(buffer.toString().startsWith("abcdefghij\n"))
        }
        screenshot("selection-restored-after-find")
    }

    @Test fun pinchRoundTrips_keepGuttersVisibleAndSingleFingerScrollWorking() {
        mount()
        for (pinned in listOf(false, true)) for (wrapped in listOf(false, true)) {
            composeRule.runOnIdle {
                state.config = state.config.copy(pinLineNumber = pinned, wordWrap = wrapped)
                state.fontSizeSp = 16f
                state.scrollOffsetXPx = 0f
                state.scrollToLine(30)
            }
            composeRule.waitForIdle()
            val originalFont = state.fontSizeSp
            pinch(expand = true, pivotFraction = 0.6f)
            assertTrue(state.fontSizeSp > originalFont)
            val enlargedFont = state.fontSizeSp
            pinch(expand = false, pivotFraction = 0.4f)
            composeRule.runOnIdle {
                assertTrue(state.fontSizeSp < enlargedFont)
                assertEquals(0f, state.scrollOffsetXPx, 0.01f)
                assertEquals(session.renderer.contentStartX(state, session.lineNumberPaint), state.contentStartXPx, 0.01f)
                assertEquals(1f, session.ui.scaleGestureVisualScale, 0f)
                assertFalse(state.isWordWrapLayoutFrozen())
            }
            screenshot("pinch-pinned-$pinned-wrap-$wrapped")
            val scrollBefore = state.scrollOffsetPx
            composeRule.onNodeWithTag("regression_editor").performTouchInput {
                swipeUp(startY = height * 0.75f, endY = height * 0.35f, durationMillis = 240L)
            }
            composeRule.runOnIdle { assertTrue(state.scrollOffsetPx > scrollBefore) }
        }
    }

    @Test fun oppositeHostAndEditorThemes_keepFindAndInjectedHoverReadable() {
        mount()
        val documentation = "int value = 123;\nDocumentation and a readable code example."
        composeRule.runOnIdle { state.onRequestHover = { documentation } }
        for ((name, scheme) in listOf("dark" to EditorColorScheme.builtinDark(), "light" to EditorColorScheme.builtinLight())) {
            composeRule.runOnIdle {
                darkHost = name == "light"
                state.colorScheme = scheme
                state.selectRange(0, 10)
                state.find.show()
            }
            composeRule.onNodeWithTag("editor_find_query").assertIsFocused()
            composeRule.waitUntil(5_000) { state.find.isCurrent }
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag("editor_find_query").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val palette = resolveEditorPopupColors(scheme)
            assertTrue(popupContrastRatio(layouts.single().layoutInput.style.color, palette.selectedSurfaceColor) >= 4.5f)
            screenshot("find-$name-editor")
            composeRule.onNodeWithTag("editor_find_close").performClick()
            composeRule.waitForIdle()
            SystemClock.sleep(300L)
            composeRule.onNodeWithTag("regression_editor").performTouchInput {
                longClick(Offset(state.contentStartXPx + state.charWidthPx * 3.5f, state.lineHeightPx * 0.5f))
            }
            composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_MORE_ACTION_TAG).performClick()
            composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_HOVER_ACTION_TAG).performClick()
            composeRule.waitUntil(5_000) { state.assistance.hoverUiState is HoverUiState.Visible }
            composeRule.onNodeWithText(documentation).assertIsDisplayed()
            assertTrue(popupContrastRatio(checkNotNull(hoverColor), palette.containerColor) >= 4.5f)
            screenshot("hover-$name-editor")
            composeRule.onNodeWithText(documentation).assertIsDisplayed()
            composeRule.runOnIdle { session.interactionController.dismissHover() }
        }
    }

    @Test fun replacementKeyboardActionsAndUndo_workAfterSearchFocusTransfer() {
        mount()
        composeRule.runOnIdle { state.find.show(replace = true); state.find.search("value_") }
        composeRule.onNodeWithTag("editor_find_replacement").performScrollTo().performClick().performTextReplacement("changed_")
        composeRule.onNodeWithTag("editor_find_replacement").performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(buffer.toString().contains("changed_0")) }
        composeRule.onNodeWithTag("editor_find_replacement").performImeAction()
        composeRule.runOnIdle {
            assertTrue(buffer.toString().contains("changed_1"))
            state.undo()
            state.undo()
            assertFalse(buffer.toString().contains("changed_"))
        }
        composeRule.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        composeRule.onNodeWithTag("editor_find_close")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        // Dispatch through Android's real input pipeline as a physical keyboard would.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(AndroidKeyEvent.KEYCODE_ENTER)
        composeRule.runOnIdle { assertFalse(state.find.visible) }
    }

    @Test fun invalidRegexAndNoMatches_recoverAndReplaceAllRemainsOneUndo() {
        mount()
        val original = buffer.toString()
        composeRule.runOnIdle {
            state.find.show(replace = true)
            state.find.updateOptions(EditorFindOptions(regex = true))
        }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("[")
        composeRule.waitUntil(5_000) { state.find.isCurrent }
        composeRule.runOnIdle {
            assertEquals(EditorFindError.InvalidQuery, state.find.error)
            assertEquals(0, state.find.matchCount)
            assertFalse(state.find.next())
            assertEquals(0, state.find.replaceAll())
        }
        screenshot("invalid-regex")
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("missing_symbol")
        composeRule.waitUntil(5_000) { state.find.isCurrent }
        composeRule.runOnIdle { assertEquals(0, state.find.matchCount); assertEquals(null, state.find.error) }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("value_(\\d+)")
        composeRule.waitUntil(5_000) { state.find.matchCount == 160 }
        composeRule.onNodeWithTag("editor_find_replacement").performScrollTo().performClick().performTextReplacement("item_$1")
        composeRule.onNodeWithTag("editor_find_replace_all").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(buffer.toString().contains("item_159"))
            assertFalse(buffer.toString().contains("value_"))
            state.undo()
            assertEquals(original, buffer.toString())
            state.redo()
            assertTrue(buffer.toString().contains("item_159"))
        }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        composeRule.runOnIdle { assertTrue(checkNotNull(session.interactionController.inputHostView).isFocused) }
    }

    @Test fun unicodeReverseMultiSelection_survivesRepeatedPanelCycles() {
        mount()
        val content = "中文😀abcdefghij\r\nsecond abc\r\nlast"
        composeRule.runOnIdle { state.replaceRange(0, buffer.length, content) }
        val original = EditorSelectionSet.of(OffsetRange(14, 4), listOf(OffsetRange(0, 2)))
        for (cycle in 0 until 6) {
            composeRule.runOnIdle {
                state.applySelectionSet(original, ensureVisible = true)
                state.find.show(replace = cycle % 2 == 0)
            }
            composeRule.onNodeWithTag("editor_find_query").performTextReplacement("abc")
            composeRule.waitUntil(5_000) { state.find.isCurrent }
            composeRule.onNodeWithTag("editor_find_close").performClick()
            composeRule.runOnIdle {
                assertEquals(original, state.selectionSet)
                assertEquals(content, buffer.toString())
                assertTrue(checkNotNull(session.interactionController.inputHostView).isFocused)
            }
        }
        screenshot("unicode-reverse-multicursor")
    }

    @Test fun horizontalPanAndPinch_preserveScrolledContentAndAllowSearchAfterwards() {
        mount()
        composeRule.runOnIdle { state.scrollOffsetXPx = 400f; state.scrollToLine(30) }
        pinch(expand = true, pivotFraction = 0.55f)
        val enlarged = state.scrollOffsetXPx
        assertTrue(enlarged > 400f)
        pinch(expand = false, pivotFraction = 0.55f)
        composeRule.runOnIdle {
            assertTrue(state.scrollOffsetXPx > 0f)
            assertTrue(state.scrollOffsetXPx < enlarged)
            assertTrue(state.scrollOffsetXPx <= state.maxHorizontalScrollOffsetPx())
            state.find.show()
        }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("value_30")
        composeRule.waitUntil(5_000) { state.find.matchCount == 1 }
        composeRule.runOnIdle { assertEquals("value_30", state.selectedText()) }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        screenshot("scrolled-pinch-then-search")
    }

    @Test fun searchOptionsAndZeroWidthRegex_keepMatchCountsAndUndoConsistent() {
        mount()
        val content = "foo Foo food\nfoo"
        composeRule.runOnIdle { state.replaceRange(0, buffer.length, content); state.moveCursorTo(0); state.find.show() }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("foo")
        composeRule.waitUntil(5_000) { state.find.matchCount == 4 }
        composeRule.runOnIdle { state.find.updateOptions(EditorFindOptions(wholeWord = true)) }
        composeRule.waitUntil(5_000) { state.find.matchCount == 3 }
        composeRule.runOnIdle { state.find.updateOptions(EditorFindOptions(caseSensitive = true, wholeWord = true)) }
        composeRule.waitUntil(5_000) { state.find.matchCount == 2 }
        composeRule.runOnIdle { state.find.updateOptions(EditorFindOptions(regex = true)) }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("(?m)^")
        composeRule.waitUntil(5_000) { state.find.matchCount == 2 }
        composeRule.runOnIdle {
            state.find.replacement = "> "
            assertEquals(2, state.find.replaceAll())
            assertEquals("> foo Foo food\n> foo", buffer.toString())
            state.undo()
            assertEquals(content, buffer.toString())
        }
    }

    @Test fun narrowPane_keepsQueryReplacementAndPrimaryActionsReachable() {
        editorWidth = 240.dp
        mount()
        composeRule.onNodeWithTag("regression_editor").performTouchInput {
            longClick(Offset(state.contentStartXPx + state.charWidthPx * 3.5f, state.lineHeightPx * 0.5f))
        }
        composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_SELECT_ALL_ACTION_TAG).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(buffer.length, state.selectionSet.primary.end) }
        composeRule.onNodeWithTag(SELECTION_CONTEXT_MENU_MORE_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("editor_selection_context_menu_action_Find").performClick()
        composeRule.runOnIdle { state.find.toggleReplacement() }
        composeRule.onNodeWithTag("editor_find_query").assertIsDisplayed().performTextReplacement("value_0")
        composeRule.onNodeWithTag("editor_find_replacement").performScrollTo().assertIsDisplayed().performTextReplacement("renamed_0")
        composeRule.onNodeWithTag("editor_find_replace_all").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("editor_find_close").assertIsDisplayed()
        screenshot("narrow-pane-replace")
        composeRule.runOnIdle {
            val host = checkNotNull(session.interactionController.inputHostView)
            val ime = host.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom
            assertTrue("Code viewport=${state.viewportHeightPx} canvas=${session.ui.canvasHeightPx} " +
                "origin=${session.ui.canvasOriginInWindowPx} root=${host.rootView.height} ime=$ime", state.viewportHeightPx >= 40f)
        }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        composeRule.runOnIdle { assertEquals(buffer.length, state.selectionSet.primary.end) }
    }

    @Test fun foldGutterHitTargets_followFontGeometryAfterPinch() {
        mount()
        composeRule.runOnIdle {
            // Match TinaCodeEditorPage's host callback wiring.
            state.onGutterFoldToggle = { line -> state.folding.toggleFoldAtLine(line) }
            state.folding.setFoldRegions(listOf(FoldRegion(0, 8)), buffer.version)
        }
        for (expand in listOf(true, false)) {
            pinch(expand, 0.5f)
            composeRule.runOnIdle { state.scrollOffsetPx = 0f }
            val zones = session.renderer.hitZones(state, session.lineNumberPaint)
            composeRule.onNodeWithTag("regression_editor").performTouchInput {
                click(Offset((zones.lineNumberEndX + zones.gutterEndX) / 2f, state.lineHeightPx / 2f))
            }
            composeRule.runOnIdle {
                assertTrue(state.folding.isFoldCollapsedAtLine(0))
                assertTrue(state.folding.isDocLineHidden(3))
                assertEquals(0f, state.scrollOffsetXPx, 0f)
            }
            screenshot(if (expand) "fold-gutter-enlarged" else "fold-gutter-reduced")
            composeRule.onNodeWithTag("regression_editor").performTouchInput {
                click(Offset((zones.lineNumberEndX + zones.gutterEndX) / 2f, state.lineHeightPx / 2f))
            }
            composeRule.runOnIdle { assertFalse(state.folding.isDocLineHidden(3)) }
        }
    }

    private fun pinch(expand: Boolean, pivotFraction: Float) {
        composeRule.onNodeWithTag("regression_editor").performTouchInput {
            val pivot = Offset(width * pivotFraction, height * 0.5f)
            val from = width * if (expand) 0.1f else 0.2f
            val to = width * if (expand) 0.2f else 0.1f
            pinch(pivot - Offset(from, 0f), pivot - Offset(to, 0f), pivot + Offset(from, 0f), pivot + Offset(to, 0f), 320L)
        }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        SystemClock.sleep(200L)
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        // Compose idleness precedes SurfaceFlinger committing a newly attached Popup.
        SystemClock.sleep(200L)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "editor-ui-regression").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            if (InstrumentationRegistry.getArguments().getString("editorOrientation") == "landscape") {
                assertTrue("Landscape evidence must be wider than tall", bitmap.width > bitmap.height)
            }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}
