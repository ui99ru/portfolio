import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is resolved from (in priority order):
//   1. a local, git-ignored keystore.properties file, or
//   2. environment variables (set by CI from GitHub Secrets).
// The private release key never lives in the repo. If neither is available
// (e.g. before secrets are configured) the build falls back to the committed
// "shared" key so CI stays green.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

android {
    namespace = "com.tinvestlite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tinvestlite"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Committed key used for debug/sideload builds so they always share one
        // signature (in-place updates without reinstall). Not for distribution.
        create("shared") {
            storeFile = rootProject.file("keystore/tinvest-lite.jks")
            storePassword = "tinvestlite"
            keyAlias = "tinvestlite"
            keyPassword = "tinvestlite"
        }

        // Secure release key — sourced from secrets, never committed.
        create("release") {
            val storePath = signingValue("storeFile", "RELEASE_STORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            } else {
                // Fallback so a release build still succeeds before secrets exist.
                storeFile = rootProject.file("keystore/tinvest-lite.jks")
                storePassword = "tinvestlite"
                keyAlias = "tinvestlite"
                keyPassword = "tinvestlite"
            }
        }
    }

    buildTypes {
        release {
            // Minification is intentionally OFF for now. This is a personal
            // sandbox/sideload app where correctness beats a smaller APK:
            // incomplete R8 keep-rules can strip reflection-based
            // kotlinx.serialization and crash at runtime when parsing API
            // responses. Re-enable with verified keep-rules before any Play
            // distribution.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
}
