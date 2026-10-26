import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
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
    }
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
}
