plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "peek"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":peek-core")
include(":peek-core-testing")
include(":peek-wire")
include(":plugin")
