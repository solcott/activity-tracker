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
        // io.github.solcott:dataresult / uistate / uistate-circuit. GitHub Packages authenticates even public reads: put a classic
        // PAT with read:packages in ~/.gradle/gradle.properties as gpr.user / gpr.key. Only that group resolves here, so a token
        // problem can't break anything else.
        maven("https://maven.pkg.github.com/solcott/kmp-dataresult") {
            name = "GitHubPackages"
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeGroup("io.github.solcott") }
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
        "app/screens",
        "app/presenters",
        "app/fixtures",
        "web/sqlite-worker",
        "server/appview",
        "server/segment-importer",
    )
    .forEach { dir ->
        val path = ":" + dir.replace('/', '-')
        include(path)
        project(path).projectDir = file(dir)
    }
