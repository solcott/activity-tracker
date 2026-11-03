plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Moderation, which Settings opens full width; its pages come from the UI's rows.
            implementation(projects.featureModerationApi)
        }
        jvmTest.dependencies { implementation(projects.featureGearApi) }
    }
}
