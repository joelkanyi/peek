import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// The on-device agent, added to an app as debugImplementation. It serves the
// app's SharedPreferences to Peek over a local socket, speaking peek-wire.
// Published to Maven Central.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mavenPublish)
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

// AGP's Kotlin-Javadoc generation trips over peek-wire's Kotlin 2.1 metadata, so
// publish an empty javadoc jar instead (Maven Central only requires one to exist).
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = false))
    coordinates("io.github.joelkanyi", "peek-runtime", version.toString())
    pom {
        name.set("Peek Runtime")
        description.set("On-device agent that serves an app's key-value storage to the Peek IDE plugin for live inspection and editing. Debug-only.")
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

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications.withType(MavenPublication::class.java).configureEach {
            artifact(javadocJar)
        }
    }
}
