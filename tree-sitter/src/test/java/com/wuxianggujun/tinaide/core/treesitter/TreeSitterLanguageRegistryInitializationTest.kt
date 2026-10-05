package com.wuxianggujun.tinaide.core.treesitter

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class TreeSitterLanguageRegistryInitializationTest {
    private val failures = mutableListOf<Throwable>()
    private val messages = mutableListOf<String>()
    private val logTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            messages.add(message)
            t?.let(failures::add)
        }
    }

    @Before
    fun setUp() {
        mockkObject(TreeSitterRuntime)
        Timber.plant(logTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(logTree)
        unmockkObject(TreeSitterRuntime)
    }

    @Test
    fun coreLoadFailure_shouldPreventGrammarResolutionAndRetainCause() {
        val failure = UnsatisfiedLinkError("core library unavailable")
        every { TreeSitterRuntime.ensureInitialized() } throws failure

        // No real grammar/JNI is loaded: the core failure must precede Class.forName.
        assertThat(TreeSitterLanguageRegistry.resolveLanguage("java")).isNull()

        verify(exactly = 1) { TreeSitterRuntime.ensureInitialized() }
        assertThat(failures).containsExactly(failure)
        assertThat(messages.single()).contains("language=java")
    }

    @Test
    fun coreLoadFailure_shouldNotCacheLanguageAsMissing() {
        val failure = UnsatisfiedLinkError("core library unavailable")
        every { TreeSitterRuntime.ensureInitialized() } throws failure

        repeat(2) {
            assertThat(TreeSitterLanguageRegistry.resolveLanguage("java")).isNull()
        }

        verify(exactly = 2) { TreeSitterRuntime.ensureInitialized() }
        assertThat(failures).containsExactly(failure, failure)
    }

    @Test
    fun metadataAndUnknownLanguages_shouldNotInitializeNativeLibrary() {
        every { TreeSitterRuntime.ensureInitialized() } throws AssertionError("Unexpected native load")

        assertThat(TreeSitterLanguageRegistry.languageNameForFile(File("Main.java"))).isEqualTo("java")
        assertThat(TreeSitterLanguageRegistry.resolveLanguageName("kt")).isEqualTo("kotlin")
        assertThat(TreeSitterLanguageRegistry.resolveLanguage("unsupported-test-language")).isNull()

        verify(exactly = 0) { TreeSitterRuntime.ensureInitialized() }
        assertThat(failures).isEmpty()
    }
}
