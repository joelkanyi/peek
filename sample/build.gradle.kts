// A tiny debuggable app that uses SharedPreferences, to test Peek (and its
// on-device agent) end to end without touching any real app.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.joelkanyi.peek.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.joelkanyi.peek.sample"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    // The agent is debug-only; it never ships in a release build.
    debugImplementation(projects.peekRuntime)
}
