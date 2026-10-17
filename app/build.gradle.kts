import java.util.Base64
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

// Declared generated resources use the AGP variant API, which also carries
// the generator task dependency to resource processing (no compatibility override).
abstract class PrepareApprovedAlien : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val parts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val ordered = parts.files.sortedBy { it.name }
        check(ordered.map { it.name } == (0..7).map { "%02d.b64".format(it) })
        val bytes = Base64.getDecoder().decode(ordered.joinToString("") { it.readText().trim() })
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha == "48d0ce0aacba2fd4264100fb1af1b6a73f54290f6c5296f4adc07d3974a50671") {
            "Approved artwork checksum mismatch"
        }
        val target = outputDirectory.file("drawable/alien_approved.webp").get().asFile
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
    }
}

androidComponents.onVariants { variant ->
    val generator = tasks.register<PrepareApprovedAlien>("prepare${variant.name.replaceFirstChar { it.uppercaseChar() }}ApprovedAlien") {
        parts.from(rootProject.fileTree("artwork/approved-gray") { include("*.b64") })
    }
    variant.sources.res?.addGeneratedSourceDirectory(generator, PrepareApprovedAlien::outputDirectory)
}

android {
    namespace = "io.github.issisx.alienflow"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.issisx.alienflow"
        minSdk = 33
        targetSdk = 36
        versionCode = 27
        versionName = "0.1.26-chat.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SOURCE_REVISION", "\"${System.getenv("GITHUB_SHA") ?: "local-unversioned"}\"")
    }
    buildFeatures { buildConfig = true }
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
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}


tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
