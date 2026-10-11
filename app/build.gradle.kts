import java.util.Base64
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

// The approved image is text-encoded for connector transport; decoding is verified
// before any Android resource processing. No runtime download or fallback artwork.
val approvedAlienRes = layout.buildDirectory.dir("generated/approved-alien/res")
val artworkParts = rootProject.fileTree("artwork/approved-gray") { include("*.b64") }
val prepareApprovedAlien = tasks.register("prepareApprovedAlien") {
    inputs.files(artworkParts)
    outputs.dir(approvedAlienRes)
    doLast {
        val parts = artworkParts.files.sortedBy { it.name }
        check(parts.map { it.name } == (0..7).map { "%02d.b64".format(it) })
        val bytes = Base64.getDecoder().decode(parts.joinToString("") { it.readText().trim() })
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha == "8850ac9bbd07c876ba00e5b4339d1b126a14d631194b07379dde0ac676888940") {
            "Approved artwork checksum mismatch"
        }
        val target = approvedAlienRes.get().file("drawable/alien_approved.webp").asFile
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareApprovedAlien) }

android {
    namespace = "io.github.issisx.alienflow"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.issisx.alienflow"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"
        buildConfigField("String", "SOURCE_REVISION", "\"${System.getenv("GITHUB_SHA") ?: "local-unversioned"}\"")
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").res.srcDir(approvedAlienRes)
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
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
