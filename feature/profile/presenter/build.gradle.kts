plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the profile opens: activity invites, starred segments and settings.
            implementation(projects.featureInvitesApi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
        }
    }
}
