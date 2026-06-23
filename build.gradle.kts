// Top-level build file. Add common build configuration that should apply
// to all sub-projects here. Plugins are declared once with `apply false`
// so the `:app` module can `apply` them with the same version. Versions are
// resolved from the `gradle/libs.versions.toml` catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
