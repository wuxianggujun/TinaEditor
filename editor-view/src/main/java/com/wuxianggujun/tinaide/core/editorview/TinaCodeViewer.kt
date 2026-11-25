package com.wuxianggujun.tinaide.core.editorview

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber

/**
 * Read-only code surface using the same renderer, styling and line caches as [TinaEditor].
 * Optional selection reuses the editor's ranges, handles and copy menu, without an input host,
 * edit gestures or language-service callbacks.
 * The caller owns the document/highlighter and supplies a bounded viewport through [modifier].
 */
@Composable
fun TinaCodeViewer(state: EditorState, modifier: Modifier = Modifier, selectable: Boolean = false) {
    val density = LocalDensity.current
    val view = LocalView.current
    val assembly = rememberEditorRenderAssembly(density, view, state.runtimeOptions)
    val selection = remember(state, assembly, density) { CodeViewerSelection(state, assembly, density) }
    val paintMemo = remember { PaintApplyMemo() }
    val horizontal = rememberScrollableState { delta ->
        selection.dismiss()
        val before = state.scrollOffsetXPx
        state.scrollByX(-delta)
        before - state.scrollOffsetXPx
    }
    val vertical = rememberScrollableState { delta ->
        selection.dismiss()
        val before = state.scrollOffsetPx
        state.scrollBy(-delta)
        before - state.scrollOffsetPx
    }
    val accessibleCode = remember(state, selectable) {
        derivedStateOf {
            @Suppress("UNUSED_EXPRESSION")
            state.textVersion
            // Labels do not consume body columns, but remain available to accessibility services.
            AnnotatedString(if (!selectable && state.config.gutterMode == EditorGutterMode.LABELS) {
                buildString {
                    repeat(state.textBuffer.lineCount) { line ->
                        if (line > 0) append('\n')
                        state.gutterDecorations[line]?.label?.let { append('[').append(it.character).append("] ") }
                        append(state.textBuffer.getLine(line))
                    }
                }
            } else state.textBuffer.substring(0, state.textBuffer.length))
        }
    }.value
    val clipboard = if (selectable) rememberViewerClipboard() else null
    val selectionModifier = if (clipboard != null) rememberCodeViewerSelectionModifier(selection, assembly, clipboard) else Modifier
    EditorTextBufferRendererSyncEffect(state, assembly.renderer)
    Canvas(
        modifier = modifier
            .testTag("tina-code-viewer")
            .clipToBounds()
            .then(selectionModifier)
            .semantics {
                text = accessibleCode
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffsetXPx },
                    maxValue = { state.maxHorizontalScrollOffsetPx() },
                )
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { state.scrollOffsetPx },
                    maxValue = { state.maxVerticalScrollOffsetPx() },
                )
                scrollBy { x, y ->
                    state.scrollByX(x)
                    state.scrollBy(y)
                    true
                }
            }
            .scrollable(horizontal, Orientation.Horizontal)
            .scrollable(vertical, Orientation.Vertical),
    ) {
        drawEditorFrame(state) {
            @Suppress("UNUSED_EXPRESSION")
            state.effectiveStylingVersion
            @Suppress("UNUSED_EXPRESSION")
            state.textVersion
            paintMemo.apply(
                textPaint = assembly.textPaint,
                lineNumberPaint = assembly.lineNumberPaint,
                typeface = state.typeface,
                textSizePx = with(density) { state.fontSizeSp.sp.toPx() },
                lineNumberForegroundArgb = state.colorScheme.lineNumberForeground.toArgb(),
            )
            val contentStart = assembly.renderer.contentStartX(state, assembly.lineNumberPaint)
            state.updateMetrics(
                lineHeightPx = paintMemo.lineHeightPx,
                charWidthPx = paintMemo.charWidthPx,
                viewportHeightPx = size.height.coerceAtLeast(1f),
                viewportWidthPx = (size.width - contentStart).coerceAtLeast(1f),
                contentStartXPx = contentStart,
            )
            assembly.renderer.render(this, state, assembly.textPaint, assembly.lineNumberPaint)
            val scrollbar = assembly.scrollbarRenderer.calculateLayout(state, size.width, size.height, density)
            assembly.scrollbarRenderer.draw(this, scrollbar, 0.7f, state.colorScheme, null)
        }
    }
    if (clipboard != null) CodeViewerSelectionMenu(selection, clipboard)
}

@Composable
private fun rememberCodeViewerSelectionModifier(
    selection: CodeViewerSelection,
    assembly: EditorRenderAssembly,
    clipboard: EditorClipboardCoordinator,
): Modifier {
    val state = selection.state
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val handles = remember(state, assembly) {
        SelectionHandleDragCoordinator(state, assembly.lineLayoutCache, assembly.selectionMagnifier) { message, _ ->
            Timber.tag("CodeViewerSelection").d(message)
        }
    }
    DisposableEffect(selection, assembly) {
        onDispose {
            assembly.selectionMagnifier.dismiss()
            state.clearSelection()
        }
    }
    LaunchedEffect(state.textVersion, state.selectionRange) {
        if (state.selectionRange?.isEmpty != false) selection.dismiss()
    }
    return Modifier
        .onGloballyPositioned { selection.coordinates = it }
        .semantics {
            val range = state.selectionRange
            textSelectionRange = TextRange(range?.anchor ?: 0, range?.caret ?: 0)
            setSelection { start, end, _ ->
                if (start !in 0..state.textBuffer.length || end !in 0..state.textBuffer.length) false else {
                    state.selectRangeFromHandleDrag(start, end)
                    true
                }
            }
            copyText {
                val selected = state.selectedText()
                if (selected.isNullOrEmpty()) false else { clipboard.copyText(selected); true }
            }
        }
        .pointerInput(selection, handles) {
            awaitPointerEventScope {
                with(handles) {
                    runDragLoop(
                        contentStartXPxProvider = { state.contentStartXPx },
                        canvasWidthPxProvider = { state.viewportWidthPx + state.contentStartXPx },
                        canvasHeightPxProvider = { state.viewportHeightPx },
                        canvasOriginInWindowPxProvider = {
                            val point = selection.coordinates?.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
                            IntOffset(point?.x?.toInt() ?: 0, point?.y?.toInt() ?: 0)
                        },
                        touchSlopPx = touchSlop,
                        density = density,
                        textPaint = assembly.textPaint,
                        onActiveHandleChanged = {},
                        onContextMenuVisibilityChanged = { selection.menuVisible = it },
                        onContextMenuOffsetChanged = { selection.menuAnchor = androidx.compose.ui.geometry.Offset(it.x.toFloat(), it.y.toFloat()) },
                        cancelPendingCompletionRequest = {},
                        followExternalSelection = true,
                    )
                }
            }
        }
        .codeViewerLongPress(selection)
}

@Composable
private fun rememberViewerClipboard(): EditorClipboardCoordinator {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(context, clipboard, scope) {
        EditorClipboardCoordinator(context, context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager, scope) {
            clipboard.setClipEntry(it.toClipEntry())
        }
    }
}

@Composable
private fun CodeViewerSelectionMenu(selection: CodeViewerSelection, clipboard: EditorClipboardCoordinator) {
    if (!selection.menuVisible) return
    val view = LocalView.current
    val density = LocalDensity.current
    val state = selection.state
    val provider = remember(selection, view, density, selection.menuAnchor, selection.coordinates) {
        CodeViewerMenuPositionProvider(selection, view, with(density) { 8.dp.roundToPx() })
    }
    EditorSelectionContextMenu(
        visible = true, positionProvider = provider, selectedText = state.selectedText(),
        colorScheme = state.colorScheme, readOnly = true,
        hoverEnabled = false, gotoDefinitionEnabled = false, findReferencesEnabled = false,
        gotoTypeDefinitionEnabled = false, gotoImplementationEnabled = false,
        codeActionsEnabled = false, renameSymbolEnabled = false, switchHeaderSourceEnabled = false,
        onCopy = { state.selectedText()?.takeIf { it.isNotEmpty() }?.let(clipboard::copyText); selection.dismiss() },
        onSelectAll = { state.selectAll() }, onDismiss = selection::dismiss,
        onCut = {}, onPaste = {}, onGotoDefinition = {}, onFindReferences = {},
        onGotoTypeDefinition = {}, onGotoImplementation = {}, onCodeActions = {},
        onRenameSymbol = {}, onSwitchHeaderSource = {}, onHover = {},
    )
}
