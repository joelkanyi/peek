import com.vanniktech.maven.publish.SonatypeHost

// peek-wire is the socket protocol shared by the plugin and the on-device agent.
// Pure Kotlin, only okio: Android-safe (no StAX, no IntelliJ). Targets JVM 17 so
// the Android runtime module can depend on it. Published to Maven Central.
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.joelkanyi"
version = providers.gradleProperty("libVersion").get()

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

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // In-memory GPG signing, only when a key is present, so local builds and
    // publishToMavenLocal work without one.
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
    coordinates("io.github.joelkanyi", "peek-wire", version.toString())
    pom {
        name.set("Peek Wire")
        description.set("Socket protocol shared by the Peek IDE plugin and its on-device agent.")
        inceptionYear.set("2026")
        url.set("https://github.com/joelkanyi/peek")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("joelkanyi")
                name.set("Joel Kanyi")
                url.set("https://github.com/joelkanyi")
            }
        }
        scm {
            url.set("https://github.com/joelkanyi/peek")
            connection.set("scm:git:git://github.com/joelkanyi/peek.git")
            developerConnection.set("scm:git:ssh://git@github.com/joelkanyi/peek.git")
        }
    }
}
