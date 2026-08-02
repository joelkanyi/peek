// Test fixtures for peek-core consumers: a scriptable FakeTransport and value
// builders, so codecs and sessions can be exercised without a real device.
// Also pure JVM 17.
plugins {
    alias(libs.plugins.kotlin)
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
    api(projects.peekCore)
    api(libs.assertk)
}
