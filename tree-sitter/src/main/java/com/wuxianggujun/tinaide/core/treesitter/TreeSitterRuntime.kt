package com.wuxianggujun.tinaide.core.treesitter

import com.itsaky.androidide.treesitter.TreeSitter

/** Shared native initialization for the editor kit and optional host preloading. */
object TreeSitterRuntime {
    private val initialization = TreeSitterNativeInitialization(TreeSitter::loadLibrary)

    /**
     * Loads the core library before any grammar class or parser is initialized.
     *
     * The core library registers JNI methods in JNI_OnLoad; loading a grammar's
     * native dependencies alone does not perform this Java-side initialization.
     * Registry users are initialized automatically. Hosts using raw Tree-sitter
     * bindings must call this first, and may also use it for optional preloading.
     *
     * Failure is propagated unchanged to the caller for contextual logging. Only
     * successful initialization is cached; a later call can retry a failed load.
     */
    fun ensureInitialized() {
        initialization.ensureInitialized()
    }
}

/** Injectable only within the module so initialization can be tested without Android JNI. */
internal class TreeSitterNativeInitialization(loadLibrary: () -> Unit) {
    // SYNCHRONIZED also makes concurrent callers wait for loading to finish.
    // A throwing initializer remains uninitialized; do not cache a failure as Unit.
    private val initialized by lazy(LazyThreadSafetyMode.SYNCHRONIZED, loadLibrary)

    fun ensureInitialized() {
        initialized
    }
}
