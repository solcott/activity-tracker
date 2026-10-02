plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.room) apply false
    // Formats the root scripts. Modules get ktfmt, detekt and sort-dependencies from activitytracker.quality.
    id("activitytracker.ktfmt")
}

tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(gradle.includedBuild("build-logic").task(":convention:ktfmtCheck")) }

tasks.named(FORMAT_TASK_NAME) { dependsOn(gradle.includedBuild("build-logic").task(":convention:ktfmtFormat")) }
