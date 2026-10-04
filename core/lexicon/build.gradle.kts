plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // @Immutable on the view models the UI renders. Annotation-only, so harmless on the AppView classpath.
            api(libs.compose.runtime.annotation)
            api(libs.kotlinx.serialization.json)
        }
    }
}
