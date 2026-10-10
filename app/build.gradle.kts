plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.issisx.alienflow"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.issisx.alienflow"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            // Deliberately unsigned: the private release key stays outside CI/repository.
            signingConfig = null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
        textReport = true
    }
}

// AGP 9 uses built-in Kotlin; a separate org.jetbrains.kotlin.android plugin is unnecessary.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.webrtc)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}
