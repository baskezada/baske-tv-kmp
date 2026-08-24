import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineProfile)
}

// Firma de release: los datos van en keystore.properties (fuera del repo).
// Debe ser la MISMA clave de subida con la que se publicó tv.baske.app, si no
// Play Store rechaza el update. Sin el archivo, release compila pero sin firmar.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasKeystore = keystoreProps.getProperty("storeFile") != null

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":shared"))

    // Compose (Jetpack / androidx) via BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Android TV Compose (10-foot UI, D-pad focus)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    // Watch Next / fila "Continuar viendo" del launcher de Android TV / Google TV
    implementation(libs.androidx.tvprovider)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Solo para inyectar el HttpClient en la pantalla Descubrir (cliente Seerr).
    implementation(libs.ktor.client.core)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    // Player (libVLC — trae libass para subtítulos ASS)
    implementation(libs.libvlc.all)
    // Player alternativo (mpv — libass + más fiable, seleccionable en Ajustes)
    implementation(libs.libmpv)

    // Frosted glass (blur de fondo) para header/bottom bar — solo se activa en phone/tablet.
    implementation(libs.haze)

    // Baseline Profile: profileinstaller aplica el perfil en runtime; el módulo
    // :baselineprofile lo genera.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}

android {
    namespace = "cl.baske.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tv.baske.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 13
        versionName = "1.0"

        // Feature flag de "Descubrir" (plugin EmbySeerr). Por defecto ON.
        // Para un build sin Descubrir (p. ej. Play Store): -PenableDiscover=false
        // → en release, R8 elimina el branch muerto y las clases Seerr del bundle;
        // la tab se reemplaza por "Buscar".
        val enableDiscover = (project.findProperty("enableDiscover") as String? ?: "true")
        buildConfigField("boolean", "ENABLE_DISCOVER", enableDiscover)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // libVLC y libmpv traen su propia copia de libc++_shared.so → tomar una.
            pickFirsts += "**/libc++_shared.so"
        }
    }
    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8: elimina código muerto, ofusca y optimiza → app más rápida y
            // liviana en producción. Requiere las keep rules de proguard-rules.pro
            // (serialización, libVLC/JNI, Koin). PROBAR el release (login + video)
            // antes de publicar, por si alguna regla de reflexión faltara.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
