// build.gradle.kts (프로젝트 루트)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)    apply false
    alias(libs.plugins.kotlin.compose)    apply false

    id("com.google.gms.google-services")        apply false
    id("com.google.devtools.ksp")               apply false
    id("com.google.dagger.hilt.android")        apply false
}