import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The Wearable Data Layer only routes messages between apps that share an applicationId and a
// signing certificate, so everything below that decides either one mirrors :app — the id, the
// ".debug" suffix, and the release keystore.
val releaseKeystoreFile = rootProject.file("app/keystore/release.keystore")
val releaseStorePassword =
    System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseSigningConfig =
    releaseKeystoreFile.isFile &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

android {
    namespace = "moe.rukamori.archivetune.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "moe.rukamori.archivetune"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE_OVERRIDE")?.trim()?.toIntOrNull() ?: 1500
        versionName =
            System.getenv("VERSION_NAME_OVERRIDE")?.trim()?.takeIf { it.isNotEmpty() } ?: "15.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)
}
