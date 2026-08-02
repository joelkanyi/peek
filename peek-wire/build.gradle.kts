// peek-wire is the socket protocol shared by the plugin and the on-device agent.
// Pure Kotlin, only okio: Android-safe (no StAX, no IntelliJ). Targets JVM 17 so
// the Android runtime module can depend on it.
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.bcv)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    api(libs.okio)

    testImplementation(kotlin("test"))
    testImplementation(libs.assertk)
}
