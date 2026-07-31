import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    // Just the Compose *compiler* (Kotlin-bundled). No org.jetbrains.compose Gradle
    // plugin — its AGP integration lags AGP 9.3.1. Compose libs come by coordinate.
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // AGP 9: the Android side of a KMP module is an androidLibrary target,
    // not androidTarget() + com.android.library.
    androidLibrary {
        namespace = "com.example.futureconflicts.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // JVM target used purely for fast, host-runnable unit tests of the game core
    // (no device/emulator needed). The app ships from androidLibrary + iOS.
    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
