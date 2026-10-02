import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("activitytracker.quality")
}

// Web Worker implementing Room's WebWorkerSQLiteDriver protocol on top of SQLite WASM + OPFS.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        useEsModules()
    }

    sourceSets {
        wasmJsMain.dependencies {
            // A val because sort-dependencies can't parse a nested npm(...) call.
            val sqliteWasmWorker = npm("sqlite-wasm-worker", layout.projectDirectory.dir("worker").asFile)

            api(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
            implementation(sqliteWasmWorker)
        }
    }
}
