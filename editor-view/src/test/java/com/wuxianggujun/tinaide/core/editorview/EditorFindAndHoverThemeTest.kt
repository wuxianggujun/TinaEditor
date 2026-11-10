package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
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
class EditorFindAndHoverThemeTest {
    @get:Rule val composeRule = createComposeRule()
    private val buffer = RopeTextBuffer("abcdefghij abc")
    private val state = EditorState(buffer)
    private var mounted by mutableStateOf(true)
    private var mountsScaffold = false

    @After fun tearDown() {
        if (mountsScaffold) {
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()
        }
        buffer.close()
    }

    @Test
    fun findAndInjectedHoverContent_followEditorThemeEvenWhenHostThemeIsOpposite() {
        var darkHost by mutableStateOf(false)
        var hoverTextColor: Color? = null
        var hoverMaterialColors: ColorScheme? = null
        state.selectRange(0, 10)
        state.find.show()
        state.seedVisibleHover("Hover documentation")
        composeRule.setContent {
            MaterialTheme(colorScheme = if (darkHost) darkColorScheme() else lightColorScheme()) {
                EditorFindBar(state, Modifier.width(240.dp))
                val session = rememberTinaEditorSession(state)
                EditorHoverOverlay(session, hoverContent = { text, modifier, _, _ ->
                    hoverTextColor = LocalContentColor.current
                    hoverMaterialColors = MaterialTheme.colorScheme
                    Text(text, modifier)
                })
            }
        }

        for (scheme in listOf(EditorColorScheme.builtinDark(), EditorColorScheme.builtinLight())) {
            composeRule.runOnIdle {
                darkHost = scheme == EditorColorScheme.builtinLight()
                state.colorScheme = scheme
            }
            composeRule.waitForIdle()
            val colors = resolveEditorPopupColors(scheme)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag("editor_find_query")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts).isNotEmpty()
            assertThat(popupContrastRatio(layouts.first().layoutInput.style.color, colors.selectedSurfaceColor)).isAtLeast(4.5f)
            assertThat(popupContrastRatio(checkNotNull(hoverTextColor), colors.containerColor)).isAtLeast(4.5f)
            val material = checkNotNull(hoverMaterialColors)
            assertThat(material.surface).isEqualTo(colors.containerColor)
            assertThat(popupContrastRatio(material.onSurface, material.surfaceContainerHighest)).isAtLeast(4.5f)
            composeRule.onNodeWithTag("editor_find_close").assertIsDisplayed()
            val fieldBounds = composeRule.onNodeWithTag("editor_find_query").fetchSemanticsNode().boundsInRoot
            val closeBounds = composeRule.onNodeWithTag("editor_find_close").fetchSemanticsNode().boundsInRoot
            assertThat(fieldBounds.width).isGreaterThan(closeBounds.width * 2f)
            assertThat(fieldBounds.right).isAtMost(closeBounds.left)
        }
    }

    @Test
    fun replacementEnterAndImeAction_replaceWhileFocusedButtonsKeepTheirOwnEnterAction() {
        state.find.show(replace = true)
        state.find.search("abc")
        composeRule.setContent { EditorFindBar(state, Modifier.width(240.dp)) }
        composeRule.onNodeWithTag("editor_find_replacement").performClick().performTextReplacement("x")
        composeRule.onNodeWithTag("editor_find_replacement").performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle {
            assertThat(buffer.toString()).isEqualTo("xdefghij abc")
        }
        composeRule.onNodeWithTag("editor_find_replacement").performImeAction()
        composeRule.runOnIdle {
            assertThat(buffer.toString()).isEqualTo("xdefghij x")
        }
        composeRule.onNodeWithTag("editor_find_close")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertThat(state.find.visible).isFalse() }
    }

    @Test
    fun constrainedHeight_keepsCloseVisibleAndReplacementReachableWithoutHidingCode() {
        state.find.show(replace = true)
        mountsScaffold = true
        composeRule.setContent {
            if (mounted) Box(Modifier.size(width = 240.dp, height = 180.dp)) {
                TinaEditorScaffold(rememberTinaEditorSession(state))
            }
        }
        composeRule.onNodeWithTag("editor_find_replacement").performScrollTo().assertIsDisplayed()
            .performTextReplacement("x")
        composeRule.onNodeWithTag("editor_find_replace_all").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("editor_find_close").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertThat(state.viewportHeightPx).isGreaterThan(0f)
            assertThat(state.find.visible).isFalse()
        }
    }

    @Test
    fun editingQueryThenClosingPanel_restoresTheFullOriginalReverseSelection() {
        state.selectRange(10, 0)
        val original = state.selectionSet
        state.find.show()
        composeRule.setContent { EditorFindBar(state, Modifier.width(240.dp)) }
        composeRule.onNodeWithTag("editor_find_query").performTextReplacement("abc")
        composeRule.runOnIdle {
            state.find.refresh()
            assertThat(state.selectedText()).isEqualTo("abc")
        }
        composeRule.onNodeWithTag("editor_find_close").performClick()
        composeRule.runOnIdle {
            assertThat(state.selectionSet).isEqualTo(original)
            assertThat(state.selectedText()).isEqualTo("abcdefghij")
            assertThat(state.find.visible).isFalse()
        }
    }
}
