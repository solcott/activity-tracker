plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// Contracts several features share: UiError, the route and visibility types, the details-form fields and the activity-list events. Feature
// API modules and :app-screens build on these; nothing here belongs to one feature.
composeStability { strictClasses.set(true) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreData)
            // Only for TextFieldState: a State holds its text fields' state, which the presenter owns.
            api(libs.compose.foundation)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.io.bytestring)

            implementation(libs.compose.runtime)
        }
    }
}
