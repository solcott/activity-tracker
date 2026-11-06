plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The feed's State holds the feed filter, and its UI embeds the filter's screen.
            api(projects.featureFeedfilterApi)
            // Others' posts (feed/posts) can be reported from the feed.
            api(projects.featureReportApi)
        }
    }
}
