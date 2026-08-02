// Root build. It configures nothing to compile itself; each module applies the
// plugins it needs. Plugin versions are pinned once here (via the version
// catalog) and applied in the modules with `apply false` kept out of the way.
plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.changelog) apply false
    alias(libs.plugins.bcv) apply false
    alias(libs.plugins.mavenPublish) apply false
}
