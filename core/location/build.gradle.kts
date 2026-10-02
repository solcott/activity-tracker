plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.coroutines.core) }
        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.play.services.location)
        }
    }
}
