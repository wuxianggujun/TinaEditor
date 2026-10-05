package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.core.treesitter.SyntaxHighlighter
import com.wuxianggujun.tinaide.core.treesitter.TreeSitterRuntime
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test

class EditorOptionalLanguageServicesTest {
    @Before
    fun setUp() {
        mockkObject(TreeSitterRuntime)
        every { TreeSitterRuntime.ensureInitialized() } throws AssertionError("Unexpected Tree-sitter load")
    }

    @After
    fun tearDown() {
        try {
            verify(exactly = 0) { TreeSitterRuntime.ensureInitialized() }
        } finally {
            unmockkObject(TreeSitterRuntime)
        }
    }

    @Test
    fun plainEditor_shouldNotRequireSyntaxOrLanguageServices() {
        val buffer = RopeTextBuffer()
        try {
            val state = EditorState(buffer, file = File("Main.java"))

            assertThat(state.highlighter).isNull()
            assertThat(state.onRequestCompletion).isNull()
            assertThat(state.onRequestHover).isNull()
            assertThat(state.onRequestSignatureHelp).isNull()
        } finally {
            buffer.close()
        }
    }

    @Test
    fun injectingSyntaxHighlighter_shouldNotAttachServicesOrAffectOtherEditors() {
        val codeBuffer = RopeTextBuffer()
        val plainBuffer = RopeTextBuffer()
        try {
            val codeEditor = EditorState(codeBuffer)
            val plainEditor = EditorState(plainBuffer)
            val highlighter = mockk<SyntaxHighlighter>()

            codeEditor.highlighter = highlighter

            assertThat(codeEditor.highlighter).isSameInstanceAs(highlighter)
            assertThat(codeEditor.onRequestCompletion).isNull()
            assertThat(codeEditor.onRequestHover).isNull()
            assertThat(codeEditor.onRequestSignatureHelp).isNull()
            assertThat(plainEditor.highlighter).isNull()
        } finally {
            codeBuffer.close()
            plainBuffer.close()
        }
    }

    @Test
    fun attachingHostService_shouldNotCreateSyntaxHighlighter() {
        val buffer = RopeTextBuffer()
        try {
            val state = EditorState(buffer)

            state.onRequestHover = { "Host-provided hover" }

            assertThat(state.highlighter).isNull()
            assertThat(state.onRequestHover).isNotNull()
        } finally {
            buffer.close()
        }
    }
}
