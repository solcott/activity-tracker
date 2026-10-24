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
            api(libs.room.runtime)

            implementation(libs.compose.runtime.annotation)
            implementation(libs.kermit)
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
