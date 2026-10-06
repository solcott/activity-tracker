plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.metro)
}

// Reads activity files (FIT, GPX, TCX, Shearwater dive XML, optionally gzipped) into a format-neutral ImportedActivity. GPX, TCX and
// Shearwater XML parse in common code with xmlutil's pull parser. Each platform decodes FIT with Garmin's official SDK: Java on Android,
// desktop and the AppView, JavaScript on web, and Swift on iOS (implemented in iosApp, see FitDecoder).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreLocation)
            api(libs.kotlinx.datetime)
            implementation(projects.coreLexicon)
            implementation(libs.xmlutil.core)
        }
        commonJvmMain.dependencies { implementation(libs.garmin.fit) }
        wasmJsMain.dependencies {
            // A val because sort-dependencies can't parse a nested npm(...) call.
            val garminFitJs = npm("@garmin/fitsdk", libs.versions.garminFit.get())
            // Without it, kotlinx-datetime on web knows only UTC and fixed offsets (see TimeZones.wasmJs.kt).
            val jsJodaTimezone = npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get())
            implementation(garminFitJs)
            implementation(jsJodaTimezone)
        }
        jvmTest.dependencies { implementation(libs.garmin.fit) }
    }
}
