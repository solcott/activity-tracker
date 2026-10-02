import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    id("activitytracker.kmp.web")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Web application shell (the wasmJs counterpart of :androidApp / iosApp): owns the Metro graph,
// main(), index.html and dev-server config. Shared UI and web actuals live in :composeApp.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Resolved out here because KGP keeps the webpack lambda on the test task: `providers` (or a script-level val)
        // referenced inside it captures the script object and breaks the configuration cache.
        val devServerPort = providers.environmentVariable("PORT").map { it.toInt() }.orElse(8080)
        outputModuleName.set("webApp")
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        // The OAuth loopback redirect is derived from the page origin, so any port
                        // works.
                        port = devServerPort.get()
                    }
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.composeApp)
            // The graph is generated here, so every module that contributes bindings must be on the
            // classpath.
            implementation(projects.coreData)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.browser)
        }
    }
}
