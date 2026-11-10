plugins {
    id("activitytracker.kmp.library")
    // Exported to Swift in debug builds, for the native views' previews and snapshot tests (dev/ios-snapshot-tests).
    id("activitytracker.swift.contract")
    alias(libs.plugins.kotlin.compose)
}

// Samples are States the UIs take as params, so they're held to the same stability as the contracts.
composeStability { strictClasses.set(true) }

// Sample states and builders for UI tests, presenter tests, previews and screenshot tests. Contracts and lexicon only: no Room, Ktor or UI.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreLexicon)
            api(projects.featureDetailApi)
            api(projects.featureDivesiteApi)
            api(projects.featureEditApi)
            api(projects.featureFeedApi)
            api(projects.featureFeedfilterApi)
            api(projects.featureGearApi)
            api(projects.featureInvitesApi)
            api(projects.featureLoginApi)
            api(projects.featureMapApi)
            api(projects.featureMediaApi)
            api(projects.featureModerationApi)
            api(projects.featurePostApi)
            api(projects.featureProfileApi)
            api(projects.featureRecordApi)
            api(projects.featureReportApi)
            api(projects.featureSearchApi)
            api(projects.featureSegmentApi)
            api(projects.featureSettingsApi)
            api(projects.featureUploadsApi)
            api(libs.dataresult.uistate)

            implementation(libs.compose.runtime)
        }
    }
}
