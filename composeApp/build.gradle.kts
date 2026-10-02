plugins {
    id("activitytracker.kmp.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    id("activitytracker.native.oauth")
}

val generateNativeNetworkConfig =
    tasks.register<GenerateNativeNetworkConfig>("generateNativeNetworkConfig") {
        description = "Generate native network config for Kotlin/Native"
        packageName = "io.github.solcott.activitytracker.app"
        appviewPublicUrl = nativeOAuth.appviewPublicUrl
        redirectScheme = nativeOAuth.redirectScheme
        outputDir = layout.buildDirectory.dir("generated/nativeNetworkConfig/kotlin")
    }

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateNativeNetworkConfig) }
        commonMain.dependencies {
            implementation(projects.coreData)
            implementation(libs.circuit.foundation)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.serialization)
            implementation(libs.compose.material.icons.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
        }
        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
        commonTest.dependencies {
            implementation(libs.circuit.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
