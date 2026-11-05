plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the activity opens: editing, gear, adding people, the route map and gallery, profiles, adding a recording, segments
            // and training zones.
            implementation(projects.featureDivesiteApi)
            implementation(projects.featureEditApi)
            implementation(projects.featureGearApi)
            implementation(projects.featureInvitesApi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureProfileApi)
            implementation(projects.featureRecordApi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
        }
    }
}
