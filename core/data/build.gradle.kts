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
            api(projects.core.atproto)
            api(projects.core.location)
            api(libs.room.runtime)
        }
        nonWebMain {
            dependencies {
                implementation(libs.cryptography.core)
                implementation(libs.sqlite.bundled)
            }
        }
        wasmJsMain.dependencies { implementation(projects.web.sqliteWorker) }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}

dependencies { listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64", "kspWasmJs").forEach { add(it, libs.room.compiler) } }

room3 { schemaDirectory(layout.projectDirectory.dir("schemas")) }
