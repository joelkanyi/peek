import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The IntelliJ plugin itself: tool window, services, and (from P1) the ddmlib
// transport. Depends on peek-core for all domain logic. Targets JVM 21, which
// the IntelliJ Platform 2024.2+ runtime requires.
plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // The IntelliJ Platform already provides Kotlin coroutines; exclude peek-core's
    // copy so it is not bundled twice (avoids classloader conflicts at runtime).
    // okio is NOT provided by the platform, so it stays bundled.
    implementation(projects.peekCore) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
    implementation(projects.peekWire)

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation(projects.peekCoreTesting)

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Empty by default; the Android plugin dependency arrives in P1 when ddmlib is used.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').filter(String::isNotBlank) })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',').filter(String::isNotBlank) })

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    // Peek contributes no IDE settings pages, so skip building searchable options.
    // This also avoids launching a full IDE during the build (faster, cheaper CI).
    buildSearchableOptions = false

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Plugin description comes from the <!-- Plugin description --> block in the root README.
        description = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    // CHANGELOG.md lives at the repo root, not in this module.
    path = rootProject.file("CHANGELOG.md").absolutePath
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }
}
