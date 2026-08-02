// peek-core is pure Kotlin/JVM: the domain model, codecs, transport seam, and
// session logic. It knows NOTHING about IntelliJ or ddmlib, so it is unit-
// testable without an IDE. Targets JVM 17 so a future Android module can
// depend on it.
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
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.peekCoreTesting)
}
