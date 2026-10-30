plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the activity opens: editing, gear, the route map and gallery, profiles, adding a recording, segments and training
            // zones.
            implementation(projects.featureEditApi)
            implementation(projects.featureGearApi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureProfileApi)
            implementation(projects.featureRecordApi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
        }
    }
}
