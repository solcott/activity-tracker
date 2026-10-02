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
    }
}

rootProject.name = "activity-tracker"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":composeApp", ":androidApp", ":webApp")

// Flat names (core/data -> :core-data) so Gradle doesn't synthesize empty :core, :web and :server parent projects.
listOf("core/lexicon", "core/atproto", "core/location", "core/data", "web/sqlite-worker", "server/appview").forEach { dir ->
    val path = ":" + dir.replace('/', '-')
    include(path)
    project(path).projectDir = file(dir)
}
