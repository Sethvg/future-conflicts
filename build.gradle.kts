plugins {
    // Declared here (apply false) so versions resolve for all subprojects; each
    // module applies the ones it needs.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
