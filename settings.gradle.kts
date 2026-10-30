@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Snapshots of io.github.solcott libraries (kmp-dataresult) from the Central Portal. Releases come from mavenCentral().
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            name = "MavenCentralSnapshots"
            mavenContent {
                snapshotsOnly()
                includeGroup("io.github.solcott")
            }
        }
    }
}

// A local kmp-dataresult checkout replaces the published artifacts when dataresult.dir (an absolute path, in
// ~/.gradle/gradle.properties) names it, so library changes build here without publishing.
providers.gradleProperty("dataresult.dir").orNull?.let(::file)?.takeIf { it.isDirectory }?.let { includeBuild(it) }

rootProject.name = "activity-tracker"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":composeApp", ":androidApp", ":webApp", ":desktopApp", ":detekt-rules")

// Flat names (core/data -> :core-data) so Gradle doesn't synthesize empty :core, :app, :web and :server parent projects.
listOf(
        "core/lexicon",
        "core/atproto",
        "core/location",
        "core/activityfile",
        "core/data",
        "app/common/api",
        "app/common/presenter",
        "app/common/presenter-testing",
        "app/common/ui",
        "app/screens",
        "app/presenters",
        "app/fixtures",
        "ui/designsystem",
        "ui/resources",
        "feature/search/api",
        "feature/search/presenter",
        "feature/search/ui",
        "feature/report/api",
        "feature/report/presenter",
        "feature/report/ui",
        "feature/media/api",
        "feature/media/presenter",
        "feature/media/ui",
        "feature/feedfilter/api",
        "feature/feedfilter/presenter",
        "feature/feedfilter/ui",
        "feature/map/api",
        "feature/map/presenter",
        "feature/map/ui",
        "feature/moderation/api",
        "feature/moderation/presenter",
        "feature/moderation/ui",
        "feature/login/api",
        "feature/login/presenter",
        "feature/login/ui",
        "web/sqlite-worker",
        "server/appview",
        "server/segment-importer",
    )
    .forEach { dir ->
        val path = ":" + dir.replace('/', '-')
        include(path)
        project(path).projectDir = file(dir)
    }
