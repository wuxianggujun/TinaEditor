package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Document-local find state. Call mutations on the editor/UI thread. */
@Stable
class EditorFindController internal constructor(private val editor: EditorState) {
    var visible by mutableStateOf(false)
        private set
    var showReplacement by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var options by mutableStateOf(EditorFindOptions())
        private set
    private var replacementText by mutableStateOf("")
    var replacement: String
        get() = replacementText
        set(value) {
            replacementText = value
            if (error == EditorFindError.InvalidReplacement) error = null
        }
    var error by mutableStateOf<EditorFindError?>(null)
        private set
    var activeIndex by mutableStateOf(-1)
        private set
    internal var focusRequest by mutableStateOf(0)
        private set
    private var result by mutableStateOf(EditorFindResult())
    private var resultVersion by mutableStateOf(-1L)
    private var resultQuery = ""
    private var resultOptions = EditorFindOptions()
    private var navigateOnRefresh = false
    private var sessionId = 0L
    private var selectionBeforeFind: EditorSelectionSet? = null
    private var selectionVersion = -1L
    private var previewSelection: EditorSelectionSet? = null

    val isCurrent: Boolean
        get() {
            // Observe the Compose version without requiring a host listener for synchronous use.
            @Suppress("UNUSED_EXPRESSION")
            editor.textVersion
            return !editor.textBuffer.isClosed && resultVersion == editor.textBuffer.version &&
                resultQuery == query && resultOptions == options
        }
    val matches: List<OffsetRange> get() = if (isCurrent) result.matches else emptyList()
    val matchCount: Int get() = matches.size

    fun show(replace: Boolean = false) {
        if (editor.textBuffer.isClosed) return
        if (!visible) {
            sessionId++
            selectionBeforeFind = editor.selectionSet.takeIf {
                it.isMultiCursor || it.primary.anchor != it.primary.caret
            }
            selectionVersion = editor.textBuffer.version
            previewSelection = null
            editor.selectedText()?.takeIf { '\n' !in it && '\r' !in it }?.let(::updateQuery)
            // Opening the panel only highlights matches. It must not replace the selection
            // with a shorter regex match, normalize its direction, or drop extra cursors.
            navigateOnRefresh = false
            activeIndex = if (isCurrent) matchIndexAtOrAfter(editor.selectionSet.primary.start) else -1
        }
        visible = true
        showReplacement = replace
        focusRequest++
    }

    fun dismiss() {
        if (!visible) return
        visible = false
        sessionId++
        navigateOnRefresh = false
        val original = selectionBeforeFind
        // Never restore stale offsets after an edit, or override a selection the user
        // made directly in the document while the find panel was open.
        if (original != null && !editor.textBuffer.isClosed && selectionVersion == editor.textBuffer.version &&
            editor.selectionSet == previewSelection) {
            editor.applySelectionSet(original, ensureVisible = true)
        }
        selectionBeforeFind = null
        previewSelection = null
    }
    fun toggleReplacement() { showReplacement = !showReplacement }

    fun updateQuery(value: String) {
        if (query == value) return
        query = value
        error = null
        navigateOnRefresh = true
    }

    fun updateOptions(value: EditorFindOptions) {
        if (options == value) return
        options = value
        error = null
        navigateOnRefresh = true
    }

    /** Synchronous API for callers without a mounted TinaEditor. UI typing uses refreshAsync. */
    fun search(query: String, options: EditorFindOptions = this.options): Int {
        updateQuery(query)
        updateOptions(options)
        refresh()
        return matchCount
    }

    internal suspend fun refreshAsync(searchDispatcher: CoroutineDispatcher = Dispatchers.Default) {
        if (!visible || editor.textBuffer.isClosed || isCurrent) return
        val requestedSession = sessionId
        val version = editor.textBuffer.version
        val requestedQuery = query
        val requestedOptions = options
        val text = editor.textBuffer.substring(0, editor.textBuffer.length)
        val found = withContext(searchDispatcher) {
            val context = currentCoroutineContext()
            EditorFindEngine.scan(text, requestedQuery, requestedOptions, checkCancelled = { context.ensureActive() })
        }
        if (visible && sessionId == requestedSession && !editor.textBuffer.isClosed &&
            version == editor.textBuffer.version && query == requestedQuery && options == requestedOptions) {
            publish(found, version)
        }
    }

    fun refresh() {
        if (editor.textBuffer.isClosed || isCurrent) return
        publish(EditorFindEngine.scan(editor.textBuffer.substring(0, editor.textBuffer.length), query, options),
            editor.textBuffer.version)
    }

    private fun publish(found: EditorFindResult, version: Long) {
        val previous = result.matches.getOrNull(activeIndex)?.start ?: editor.selectionSet.primary.start
        result = found
        resultVersion = version
        resultQuery = query
        resultOptions = options
        error = found.error
        activeIndex = matchIndexAtOrAfter(previous)
        if (navigateOnRefresh) {
            navigateOnRefresh = false
            revealActive()
        }
    }

    fun next(): Boolean = navigate(1)
    fun previous(): Boolean = navigate(-1)

    private fun matchIndexAtOrAfter(offset: Int): Int {
        if (result.matches.isEmpty()) return -1
        return result.matches.indexOfFirst { it.start >= offset }.takeIf { it >= 0 } ?: 0
    }

    private fun navigate(delta: Int): Boolean {
        val active = matches.getOrNull(activeIndex)
        val selection = editor.selectionSet.primary
        val activeSelected = active != null && selection.start == active.start && selection.end == active.end
        refresh()
        if (matchCount == 0) return false
        // Opening the panel leaves the original selection untouched. The first forward
        // action should reveal the indicated match, not silently skip over it.
        if (activeSelected || delta < 0) activeIndex = Math.floorMod(activeIndex + delta, matchCount)
        return revealActive()
    }

    private fun revealActive(): Boolean {
        val range = matches.getOrNull(activeIndex) ?: return false
        editor.applySelectionSet(EditorSelectionSet.of(range), ensureVisible = true)
        if (visible) previewSelection = editor.selectionSet
        return true
    }

    fun replaceCurrent(): Boolean {
        refresh()
        val target = matches.getOrNull(activeIndex) ?: return false
        val oldLength = editor.textBuffer.length
        val changed = replace(target)
        if (changed > 0) {
            // Do not repeatedly replace an inserted match at the same position (including ^/$).
            val resumeOffset = target.end + editor.textBuffer.length - oldLength
            activeIndex = matches.indexOfFirst { it.start >= resumeOffset }.takeIf { it >= 0 }
                ?: if (matches.isEmpty()) -1 else 0
            revealActive()
        }
        return changed > 0
    }

    fun replaceAll(): Int = replace(null)

    private fun replace(target: OffsetRange?): Int {
        if (editor.textBuffer.isClosed) return 0
        val found = EditorFindEngine.scan(
            editor.textBuffer.substring(0, editor.textBuffer.length), query, options, replacement, target
        )
        error = found.error
        if (found.error != null || found.edits.isEmpty()) return 0
        applyMultiCursorEditPlan(editor, EditorEditPlan(found.edits,
            mapSelectionSetThroughEdits(editor.selectionSet, found.edits)), "findReplace")
        refresh()
        return found.edits.size
    }
}
