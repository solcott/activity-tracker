plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ProfileState holds report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
