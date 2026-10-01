plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.treesitter.grammars"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    outputToConsole.set(true)
}

dependencies {
    api("com.itsaky.androidide.treesitter:tree-sitter-aidl:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-bash:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-c:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-cmake:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-cpp:4.3.2")
    api(libs.tree.sitter.java)
    api("com.itsaky.androidide.treesitter:tree-sitter-json:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-log:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-make:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-properties:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-python:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-rust:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-toml:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-xml:4.3.2")
    api("com.itsaky.androidide.treesitter:tree-sitter-yaml:4.3.2")
}
