// The Kotlin plugins here also lift the Kotlin compiler AGP 9 brings (2.2.x) to the catalog's version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.baselineprofile) apply false
}
