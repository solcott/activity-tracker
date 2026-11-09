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
            // @Stable on ImportSource, which the import screen's events carry. Annotation-only, so harmless on the AppView classpath.
            implementation(libs.compose.runtime.annotation)
            // Zip members' raw deflate, inflated a piece at a time in common code (InflaterInput), and gzip off the web.
            implementation(libs.kmp.zlib)
            // Garmin Connect exports' activity summaries.
            implementation(libs.kotlinx.serialization.json)
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

    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=kotlin.js.ExperimentalWasmJsInterop")
    }
}

// `-PworkoutSamples` lets WriteWorkoutSamples write the sample activity files to build/workout-fixtures (see the activity-files rule);
// without it that test skips, so `check` writes nothing.
if (providers.gradleProperty("workoutSamples").isPresent) {
    val samples = isolated.rootProject.projectDirectory.dir("build/workout-fixtures")
    tasks.named<Test>("jvmTest") {
        systemProperty("workoutSamples", samples.asFile.absolutePath)
        outputs.dir(samples)
    }
}
