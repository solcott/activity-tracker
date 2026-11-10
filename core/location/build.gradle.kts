plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)

            // @Immutable on GeoPoint, which a route map's state carries (RouteEnds). Annotation-only, so harmless on the AppView classpath.
            implementation(libs.compose.runtime.annotation)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.play.services.location)
        }
    }
}
