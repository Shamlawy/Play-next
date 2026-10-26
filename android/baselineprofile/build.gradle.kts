// Makes the Baseline Profile on a connected device (CI's emulator in android-release.yml), so the phone's copy is
// compiled ahead and doesn't stutter on first use. `./gradlew :app:generateReleaseBaselineProfile`.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.shamlawy.playnext.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
