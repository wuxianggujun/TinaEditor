pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("external/tina-android-tree-sitter") {
    dependencySubstitution {
        substitute(module("com.itsaky.androidide.treesitter:android-tree-sitter")).using(project(":android-tree-sitter"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-java")).using(project(":tree-sitter-java"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-json")).using(project(":tree-sitter-json"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-c")).using(project(":tree-sitter-c"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-cpp")).using(project(":tree-sitter-cpp"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-bash")).using(project(":tree-sitter-bash"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-yaml")).using(project(":tree-sitter-yaml"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-make")).using(project(":tree-sitter-make"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-cmake")).using(project(":tree-sitter-cmake"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-rust")).using(project(":tree-sitter-rust"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-toml")).using(project(":tree-sitter-toml"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-aidl")).using(project(":tree-sitter-aidl"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-kotlin")).using(project(":tree-sitter-kotlin"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-log")).using(project(":tree-sitter-log"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-properties")).using(project(":tree-sitter-properties"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-python")).using(project(":tree-sitter-python"))
        substitute(module("com.itsaky.androidide.treesitter:tree-sitter-xml")).using(project(":tree-sitter-xml"))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tina-editor-kit"
include(":core:editor-api")
include(":core:text-engine")
include(":core:tree-sitter")
include(":core:language-support")
include(":core:tree-sitter-grammars")
include(":core:editor-view")
project(":core:editor-api").projectDir = file("editor-api")
project(":core:text-engine").projectDir = file("text-engine")
project(":core:tree-sitter").projectDir = file("tree-sitter")
project(":core:language-support").projectDir = file("language-support")
project(":core:tree-sitter-grammars").projectDir = file("tree-sitter-grammars")
project(":core:editor-view").projectDir = file("editor-view")
