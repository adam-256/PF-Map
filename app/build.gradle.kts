import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun prop(name: String, default: String = ""): String =
    (project.findProperty(name) as String?) ?: System.getenv(name) ?: localProps.getProperty(name) ?: default

// "owner/repo" whose GitHub release "clubs-data" hosts the latest club list.
val dataRepo = prop("PF_DATA_REPO", System.getenv("GITHUB_REPOSITORY") ?: "adam-256/pf-map")
val buildNumber = prop("BUILD_NUMBER", "1").toInt()

android {
    namespace = "com.pfmap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pfmap.app"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        buildConfigField("String", "DATA_REPO", "\"$dataRepo\"")
        // Optional build-time Google Places API key (can also be entered in-app under Settings).
        buildConfigField("String", "PLACES_API_KEY", "\"${prop("PLACES_API_KEY")}\"")
    }

    signingConfigs {
        // A fixed, committed key so every CI build can be installed over the previous one.
        // Provide RELEASE_KEYSTORE_* secrets to sign with a private key instead.
        create("shared") {
            val ks = prop("RELEASE_KEYSTORE_FILE")
            if (ks.isNotEmpty()) {
                storeFile = file(ks)
                storePassword = prop("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("pfmap-shared.keystore")
                storePassword = "android"
                keyAlias = "pfmap"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
            // Real phones are ARM; dropping emulator-only x86 MapLibre binaries halves the APK.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    // The bundled club snapshot lives in /data so the app and the data pipeline share one file.
    sourceSets["main"].assets.srcDirs("src/main/assets", "../data")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre)
    testImplementation(libs.junit)
}
