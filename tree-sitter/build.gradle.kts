plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.treesitter"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
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
    implementation(project(":core:editor-api"))
    implementation(project(":core:language-support"))
    implementation(project(":core:text-engine"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)

    // Tree-sitter core
    api(libs.tree.sitter)

    testImplementation(libs.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
    testImplementation(libs.tests.mockk)
    testImplementation(libs.tests.kotlinx.coroutines)
}
