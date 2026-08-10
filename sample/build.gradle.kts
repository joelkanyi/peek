// A debuggable app that seeds realistic (but entirely fake) data into every store
// Peek reads: SharedPreferences, Preferences DataStore, Proto DataStore, and a
// Multiplatform Settings store. Used to demo and test Peek end to end.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
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

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.multiplatform.settings)
    // The agent is debug-only; it never ships in a release build.
    debugImplementation(projects.peekRuntime)
}
