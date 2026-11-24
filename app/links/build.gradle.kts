plugins { id("activitytracker.kmp.library") }

// The app's links (ui/deep-links): the path of every screen a link can open, the stack a link builds, and the URL forms that are links.
// Pure Kotlin over the screens' contracts, so it parses before any graph or session exists (Android's intent, the web's start-up URL)
// and in plain JVM tests. It sees features only through their API modules (dev/module-boundaries) and isn't exported to Swift: Swift
// hands a link over as text through IosApp.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreLexicon)
            api(libs.circuit.runtime)

            implementation(projects.appCommonApi)
            implementation(projects.featureCommentsApi)
            implementation(projects.featureDetailApi)
            implementation(projects.featureDivesiteApi)
            implementation(projects.featureEditApi)
            implementation(projects.featureFeedApi)
            implementation(projects.featureGearApi)
            implementation(projects.featureInvitesApi)
            implementation(projects.featureLoginApi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureModerationApi)
            implementation(projects.featurePostApi)
            implementation(projects.featureProfileApi)
            implementation(projects.featureRecordApi)
            implementation(projects.featureSearchApi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
            implementation(projects.featureUploadsApi)
            implementation(libs.ktor.http)
        }
        jvmTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
