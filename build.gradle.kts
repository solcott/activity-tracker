import com.ncorti.ktfmt.gradle.KtfmtExtension
import dev.detekt.gradle.extensions.DetektExtension

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
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.sort.dependencies) apply false
}

// build-logic applies the same ktfmt style in its own build.
ktfmt {
    kotlinLangStyle()
    maxWidth.set(140)
}

subprojects {
    pluginManager.apply(rootProject.libs.plugins.ktfmt.get().pluginId)
    extensions.configure<KtfmtExtension> {
        kotlinLangStyle()
        maxWidth.set(140)
    }

    pluginManager.apply(rootProject.libs.plugins.sort.dependencies.get().pluginId)

    pluginManager.apply(rootProject.libs.plugins.detekt.get().pluginId)
    extensions.configure<DetektExtension> {
        // Plain (no type resolution) analysis of every source set; the default only covers
        // src/main and src/test, which KMP modules don't have.
        source.setFrom(fileTree("src") { include("**/*.kt") })
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(file("detekt-baseline.xml"))
        parallel.set(true)
    }
    dependencies { "detektPlugins"(rootProject.libs.compose.rules.detekt) }
}

tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":convention:ktfmtCheck")) }

tasks.named("ktfmtFormat") { dependsOn(gradle.includedBuild("build-logic").task(":convention:ktfmtFormat")) }
