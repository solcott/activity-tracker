plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreActivityfile)
            api(projects.coreAtproto)
            api(projects.coreLocation)
            api(libs.dataresult)
            // Page, which paged reads return.
            api(libs.dataresult.uistate)
            // PlatformFile, which picked files keep (importsave/) so a save can reopen them after process death.
            api(libs.filekit.core)
            api(libs.room.runtime)

            implementation(libs.compose.runtime.annotation)
            implementation(libs.kermit)
            implementation(libs.kotlinx.datetime)
        }
        nonWebMain {
            dependencies {
                implementation(libs.cryptography.core)
                implementation(libs.sqlite.bundled)
            }
        }
        // The desktop app's secret key lives in the OS credential store (DesktopSecretCipher).
        jvmMain.dependencies { implementation(libs.java.keyring) }
        wasmJsMain.dependencies { implementation(projects.webSqliteWorker) }
        commonTest.dependencies {
            implementation(libs.kermit.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}

dependencies { listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64", "kspWasmJs").forEach { add(it, libs.room.compiler) } }

room3 { schemaDirectory(layout.projectDirectory.dir("schemas")) }

// Each platform target's Room run must generate AppDatabaseConstructor, the actual for the commonMain expect. A run that generated
// nothing once succeeded and was stored in the build cache, so every later compileAndroidMain restored it and failed with
// NO_ACTUAL_FOR_EXPECT. Failing such a run keeps it out of the cache, and the next build runs KSP again.
val roomKspTasks = setOf("kspAndroidMain", "kspKotlinJvm", "kspKotlinIosArm64", "kspKotlinIosSimulatorArm64", "kspKotlinWasmJs")

tasks
    .named { it in roomKspTasks }
    .configureEach {
        val generated = outputs.files
        val taskName = name
        doLast {
            check(generated.asFileTree.any { it.name == "AppDatabaseConstructor.kt" }) {
                "$taskName generated no AppDatabaseConstructor; rerun with --rerun --no-build-cache"
            }
        }
    }
