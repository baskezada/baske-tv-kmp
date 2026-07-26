buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // R8 más nuevo que el que trae AGP 9.0.1, para que parsee la metadata de
        // Kotlin 2.4.10 (elimina el warning "error parsing kotlin metadata" en R8).
        classpath("com.android.tools:r8:9.1.31")
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineProfile) apply false
}
