plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// Samples are States the UIs take as params, so they're held to the same stability as the contracts.
composeStability { strictClasses.set(true) }

// Sample states and builders for UI tests, presenter tests, previews and screenshot tests. Contracts and lexicon only: no Room, Ktor or UI.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.appScreens)
            api(projects.coreLexicon)
            api(projects.featureEditApi)
            api(projects.featureFeedfilterApi)
            api(projects.featureGearApi)
            api(projects.featureLoginApi)
            api(projects.featureMapApi)
            api(projects.featureMediaApi)
            api(projects.featureModerationApi)
            api(projects.featureReportApi)
            api(projects.featureSearchApi)
            api(projects.featureSegmentApi)
            api(projects.featureSettingsApi)
            api(libs.dataresult.uistate)

            implementation(libs.compose.runtime)
        }
    }
}
