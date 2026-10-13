@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    // Kotlin compilation is provided by AGP's built-in Kotlin support since AGP 9.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "2.3.4"
val appVersionCode = 13
val buildTime = System.currentTimeMillis()

// ---------------------------------------------------------------------------
// Release signing
//
// Create `keystore.properties` in the repository root (it is git-ignored, see
// `keystore.properties.example`) with:
//
//     storeFile=release.jks        # relative paths resolve against the repository root
//     storePassword=...
//     keyAlias=...
//     keyPassword=...
//
// The same values may instead be supplied through environment variables, which is what a CI
// should use (KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD).
//
// When neither is present the build still succeeds and `assembleRelease` produces an
// *unsigned* APK, so a plain `assembleDebug`/`assembleRelease` never needs a keystore.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, environmentKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf(String::isNotBlank)
        ?: System.getenv(environmentKey)?.takeIf(String::isNotBlank)

val releaseStoreFilePath = signingValue("storeFile", "KEYSTORE_PATH")

android {
    namespace = "cn.fuckhome.xiaowine"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "cn.fuckhome.xiaowine"
        // 33 (Android 13) because `miuix-blur` declares minSdkVersion 33; the About page's frosted
        // glass and animated background are built on it.
        minSdk = 33
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        if (releaseStoreFilePath != null) {
            // AGP 9's container is `NamedDomainObjectContainer<out ApkSigningConfig>`, so the
            // config has to be configured through the returned reference instead of a lambda.
            val releaseSigning = maybeCreate("release")
            releaseSigning.storeFile = rootProject.file(releaseStoreFilePath)
            releaseSigning.storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
            releaseSigning.keyAlias = signingValue("keyAlias", "KEY_ALIAS")
            releaseSigning.keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // Miuix is published with JVM target 21, so consumers must match it to inline its code.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // IMPORTANT: META-INF/xposed/* carries the modern Xposed module metadata
            // (module.prop / java_init.list / scope.list) and must stay in the APK.
            // Never exclude all of /META-INF/** here.
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
            excludes += "/kotlin/**"
            excludes += "/*.txt"
            excludes += "/*.bin"
        }
        dex {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Fuck_Home-$appVersionName($appVersionCode)-${variant.name}-$buildTime.apk")
        }
    }
}

dependencies {
    // Modern Xposed API (libxposed). Provided by the framework at runtime, never bundled.
    compileOnly("io.github.libxposed:api:102.0.0")
    // Modern Xposed service (remote preferences / scope requests) — bundled into the module app.
    implementation("io.github.libxposed:service:102.0.0")

    // Compose Multiplatform runtime used by the settings UI (Miuix is built against 1.12.x).
    implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
    implementation("org.jetbrains.compose.ui:ui:1.12.1")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Miuix (HyperOS / MIUI design system)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    // Miuix navigation runtime: HyperOS page transitions + predictive back
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4")
    // Miuix blur: frosted-glass backdrop + colour blending behind the About page's cards and its
    // animated background. Declares minSdkVersion 33, hence the matching minSdk above.
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
}
