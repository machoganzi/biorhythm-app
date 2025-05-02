// settings.gradle.kts
pluginManagement {
    plugins {
        id("com.android.application")               version "7.4.2"      apply false
        id("org.jetbrains.kotlin.android")          version "1.8.21"     apply false
        id("com.google.dagger.hilt.android")        version "2.47"       apply false
        id("com.google.gms.google-services")        version "4.4.2"      apply false
        id("com.google.devtools.ksp")               version "1.9.22-1.0.17" apply false
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Biorhythm_Project"
include(":app")
