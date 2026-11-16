import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins { id("activitytracker.kmp.web") }

// Web Worker implementing Room's WebWorkerSQLiteDriver protocol on top of SQLite WASM + OPFS.
kotlin {
    @OptIn(ExperimentalWasmDsl::class) wasmJs { useEsModules() }

    sourceSets {
        wasmJsMain.dependencies {
            npm("sqlite-wasm-worker", layout.projectDirectory.dir("worker").asFile)

            api(libs.sqlite.web)
        }
    }
}
