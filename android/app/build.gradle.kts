import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

// The release counter (android/version.properties) is the version; the release workflow passes
// -Ppn.versionCode = max(release, newest android-v tag + 1) so it never goes down.
val release: Int = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("release").trim().toInt()
val code: Int = providers.gradleProperty("pn.versionCode").orNull?.trim()?.toInt() ?: release

// The real key only exists on main's release builds (android-release.yml). Without it the release
// build signs with the debug key: a throwaway that can never update the phone's app.
val storeFilePath: String? = System.getenv("PN_SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
val repoDir: String = rootDir.parentFile.absolutePath

/**
 * The app's content from index.html (colours, themes, the backup alphabet, the tab icons…): tools/android-gen.mjs
 * writes it as Kotlin into build/generated/pn before every compile, so it's never typed twice and a table that changes
 * shape fails the build (docs/ANDROID-PLAN.md gate 7). Needs `node` on the PATH.
 */
abstract class PnGen : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val page: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val script: RegularFileProperty
    @get:OutputDirectory abstract val out: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun run() {
        exec.exec {
            commandLine("node", script.get().asFile.absolutePath, "--page", page.get().asFile.absolutePath, "--out", out.get().asFile.absolutePath)
        }
    }
}

val pnGen = tasks.register<PnGen>("pnGen") {
    page.set(rootProject.layout.projectDirectory.file("../index.html"))
    script.set(rootProject.layout.projectDirectory.file("../tools/android-gen.mjs"))
    out.set(layout.buildDirectory.dir("generated/pn"))
}

android {
    namespace = "io.github.shamlawy.playnext"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.shamlawy.playnext" // never changes: a new id means a reinstall
        minSdk = 36
        targetSdk = 37
        versionCode = code
        versionName = code.toString()
        buildConfigField("String", "HELPER", "\"https://playnext-helper.hussamnabil48.workers.dev\"")
        buildConfigField("String", "WEB_ORIGIN", "\"https://shamlawy.github.io\"")
        buildConfigField("String", "REPO", "\"Shamlawy/Play-next\"")
    }

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storeType = "pkcs12"
                storePassword = System.getenv("PN_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("PN_SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "playnext"
                keyPassword = System.getenv("PN_SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("PN_SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // Roborazzi: the fonts and icons draw as on the phone
        // the tests read the repo's own files (worker/helper.js, whatsnew.txt, version.properties)
        unitTests.all { it.systemProperty("pn.repo", repoDir) }
    }
}

androidComponents {
    onVariants { v -> v.sources.kotlin?.addGeneratedSourceDirectory(pnGen, PnGen::out) }
}

baselineProfile {
    // Generated on the CI emulator by android-release.yml, never committed.
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.work.runtime.ktx)
    implementation(libs.metrics.performance)
    implementation(libs.profileinstaller)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    "baselineProfile"(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    // the approved pictures live in the repo; android-check compares every run with them (verifyRoborazziDebug)
    outputDir.set(file("src/test/screenshots"))
}
