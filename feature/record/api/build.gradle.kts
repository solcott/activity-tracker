plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // TrimOffer's texts are the trim screen's (feed/activity-trim).
            api(projects.featureTrimApi)
        }
    }
}
