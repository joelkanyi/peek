// The on-device agent, added to an app as debugImplementation. It serves the
// app's SharedPreferences to Peek over a local socket, speaking peek-wire.
//
// Maven Central publishing is deferred: AGP's Kotlin-Javadoc generation currently
// fails on peek-wire's Kotlin 2.1 metadata. Needs a Dokka or empty-javadoc-jar
// fix before publishing (see RELEASING.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

group = "io.github.joelkanyi"
version = providers.gradleProperty("libVersion").get()

android {
    namespace = "io.github.joelkanyi.peek.runtime"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(projects.peekWire)
}
