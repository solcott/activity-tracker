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

include(
    ":core:lexicon",
    ":core:atproto",
    ":core:location",
    ":core:data",
    ":web:sqlite-worker",
    ":composeApp",
    ":androidApp",
    ":webApp",
    ":server:appview",
)
