plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.metro)
}

// Reads activity files (FIT, GPX, TCX, optionally gzipped) into a format-neutral ImportedActivity. GPX and TCX parse in common code with
// xmlutil's pull parser. Each platform decodes FIT with Garmin's official SDK: Java on Android, desktop and the AppView, JavaScript on web,
// and Swift on iOS (implemented in iosApp, see FitDecoder).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreLocation)
            implementation(projects.coreLexicon)
            implementation(libs.xmlutil.core)
        }
        commonJvmMain.dependencies { implementation(libs.garmin.fit) }
        wasmJsMain.dependencies {
            // A val because sort-dependencies can't parse a nested npm(...) call.
            val garminFitJs = npm("@garmin/fitsdk", libs.versions.garminFit.get())
            implementation(garminFitJs)
        }
        jvmTest.dependencies { implementation(libs.garmin.fit) }
    }
}
