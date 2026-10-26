// Makes the app's Baseline Profile on the CI emulator (android-release.yml: :app:generateReleaseBaselineProfile), so the
// phone gets a build compiled ahead of time: a sideloaded app gets no cloud profiles, so this is what keeps first use smooth.
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
    useConnectedDevices = true // the emulator android-release.yml starts
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
