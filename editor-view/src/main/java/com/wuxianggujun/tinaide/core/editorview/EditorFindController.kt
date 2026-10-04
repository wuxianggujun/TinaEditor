package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
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

    val isCurrent: Boolean
        get() {
            // Observe the Compose version without requiring a host listener for synchronous use.
            @Suppress("UNUSED_EXPRESSION")
            editor.textVersion
            return resultVersion == editor.textBuffer.version && resultQuery == query && resultOptions == options
        }
    val matches: List<OffsetRange> get() = if (isCurrent) result.matches else emptyList()
    val matchCount: Int get() = matches.size

    fun show(replace: Boolean = false) {
        visible = true
        showReplacement = replace
        editor.selectedText()?.takeIf { '\n' !in it && '\r' !in it }?.let(::updateQuery)
        focusRequest++
    }

    fun dismiss() { visible = false }
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

    internal suspend fun refreshAsync() {
        if (isCurrent) return
        val version = editor.textBuffer.version
        val requestedQuery = query
        val requestedOptions = options
        val text = editor.textBuffer.substring(0, editor.textBuffer.length)
        val found = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            EditorFindEngine.scan(text, requestedQuery, requestedOptions, checkCancelled = { context.ensureActive() })
        }
        if (version == editor.textBuffer.version && query == requestedQuery && options == requestedOptions) {
            publish(found, version)
        }
    }

    fun refresh() {
        if (isCurrent) return
        publish(EditorFindEngine.scan(editor.textBuffer.substring(0, editor.textBuffer.length), query, options),
            editor.textBuffer.version)
    }

    private fun publish(found: EditorFindResult, version: Long) {
        val previous = result.matches.getOrNull(activeIndex)?.start ?: editor.cursorOffset
        result = found
        resultVersion = version
        resultQuery = query
        resultOptions = options
        error = found.error
        activeIndex = if (found.matches.isEmpty()) -1 else
            found.matches.indexOfFirst { it.start >= previous }.takeIf { it >= 0 } ?: 0
        if (navigateOnRefresh) {
            navigateOnRefresh = false
            revealActive()
        }
    }

    fun next(): Boolean = navigate(1)
    fun previous(): Boolean = navigate(-1)

    private fun navigate(delta: Int): Boolean {
        refresh()
        if (matchCount == 0) return false
        activeIndex = Math.floorMod(activeIndex + delta, matchCount)
        return revealActive()
    }

    private fun revealActive(): Boolean {
        val range = matches.getOrNull(activeIndex) ?: return false
        editor.applySelectionSet(EditorSelectionSet.of(range), ensureVisible = true)
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
