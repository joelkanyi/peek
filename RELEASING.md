# Peek — Releasing

Two release trains. Do not mix them.

| Train | Modules | Destination |
|---|---|---|
| Libraries | `peek-core`, `peek-core-testing`, `peek-wire` (from P6), `peek-runtime` (from P6) | Maven Central, `io.github.joelkanyi` |
| Plugin | `plugin` | JetBrains Marketplace |

All artifacts Apache-2.0. License header file applied by Spotless to every
source file.

## Shared build standards (both trains)

- `explicitApi()` on every published module; binary compatibility validator
  with committed `.api` dumps (`.klib.api` once `peek-wire` gains non-JVM
  targets). `./gradlew apiCheck` in CI; `./gradlew apiDump` only as a
  deliberate, reviewed act.
- Spotless + ktlint, `kotlin.code.style=official`, license header file
  (`spotless/copyright.kt`).
- Version catalog (`gradle/libs.versions.toml`), typesafe project accessors
  (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`).
- Clean Gradle Kotlin DSL. Single `VERSION_NAME` in root `gradle.properties`
  for the library train; plugin version tracked separately in
  `gradle.properties` (`pluginVersion`) because the trains are independent.

## Library train (Maven Central)

Publishing via the vanniktech `com.vanniktech.maven.publish` plugin.

Root `gradle.properties`:
```properties
SONATYPE_HOST=CENTRAL_PORTAL
RELEASE_SIGNING_ENABLED=true
GROUP=io.github.joelkanyi
VERSION_NAME=0.1.0-SNAPSHOT
POM_LICENSE_NAME=The Apache Software License, Version 2.0
POM_LICENSE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_DEVELOPER_ID=joelkanyi
POM_DEVELOPER_NAME=Joel Kanyi
POM_DEVELOPER_URL=https://github.com/joelkanyi
POM_SCM_URL=https://github.com/joelkanyi/peek
POM_SCM_CONNECTION=scm:git:git://github.com/joelkanyi/peek.git
POM_SCM_DEV_CONNECTION=scm:git:ssh://git@github.com/joelkanyi/peek.git
POM_URL=https://github.com/joelkanyi/peek
```
Per-module `gradle.properties`: `POM_ARTIFACT_ID` and `POM_NAME` only.

Signing: in-memory GPG, applied ONLY when the key is present so local builds
and `publishToMavenLocal` work without one:
```kotlin
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
```

Credentials — never committed; Gradle properties in `~/.gradle/gradle.properties`
or CI env vars:
```
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

### Library release flow (Kepler flow)
1. Ensure `main` is green: `./gradlew build apiCheck spotlessCheck`.
2. Edit `gradle.properties`: drop `-SNAPSHOT` from `VERSION_NAME`.
3. Update CHANGELOG.md; update README dependency snippets.
4. Commit: `Prepare release 0.x.y`.
5. `./gradlew publishAndReleaseToMavenCentral --no-configuration-cache`
6. Tag: `git tag -a v0.x.y -m "0.x.y"` and push commit + tag.
7. Bump `VERSION_NAME` to next `-SNAPSHOT`, commit `Prepare next development version`.
8. Create GitHub release from the tag with the changelog section.

## Plugin train (JetBrains Marketplace)

Uses the IntelliJ Platform Gradle Plugin tasks already scaffolded in this
repo (template's `build.yml`/`release.yml` workflows adapted, My* branding
removed).

- Plugin signing: JetBrains Marketplace signing via env vars
  `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`
  (`signPlugin` task). Never committed.
- Publish token: `PUBLISH_TOKEN` env var (`publishPlugin` task).
- Pre-publish gate: `./gradlew verifyPlugin` (Plugin Verifier over the CI
  matrix: IC+Android and Android Studio builds) + `test`.

### Plugin release flow
1. `main` green: `./gradlew test verifyPlugin`.
2. Set `pluginVersion` in `gradle.properties`; update change-notes section of
   CHANGELOG.md (fed into `patchPluginXml`).
3. Commit `Prepare plugin release 0.x.y`.
4. `./gradlew signPlugin publishPlugin`.
5. Tag `plugin-v0.x.y`, push, GitHub release.

Note: `plugin` depends on `peek-core` via project dependency; a Marketplace
release does NOT require a Maven Central release (core is bundled into the
plugin zip). Only `peek-runtime`/`peek-wire` consumers require published
library versions — keep the agent protocol version compatibility table in
CHANGELOG.md from P6 on.
