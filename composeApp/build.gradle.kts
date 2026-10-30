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
        labelerDid = nativeOAuth.labelerDid
        outputDir = layout.buildDirectory.dir("generated/nativeNetworkConfig/kotlin")
    }

// The strings and drawables live in :ui-resources, whose Res keeps this module's old package, so this module must not generate its own.
compose.resources { generateResClass = never }

// UI tests (AppPreview and :app-fixtures' samples), and Skia for tests that encode images (PhotoCompressionTest).
composeUiTests { jvm = true }

kotlin {
    // SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // So iosApp's SwiftFitDecoder sees FitDecoder and its DTOs under their own names.
            export(projects.coreActivityfile)
            // Calf's UIKit components need it exported to work on iOS.
            export(libs.calf.ui)
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateNativeNetworkConfig) }
        commonMain.dependencies {
            // api so the framework can export it (see the framework block above).
            api(libs.calf.ui)

            implementation(projects.appCommonUi)
            // Only so the platform Metro graphs here aggregate the presenter factories. UI code never imports from it.
            implementation(projects.appPresenters)
            implementation(projects.appScreens)
            implementation(projects.coreData)
            implementation(projects.featureFeedfilterApi)
            implementation(projects.featureFeedfilterPresenter)
            implementation(projects.featureFeedfilterUi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMapPresenter)
            implementation(projects.featureMapUi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureMediaPresenter)
            implementation(projects.featureMediaUi)
            implementation(projects.featureModerationApi)
            implementation(projects.featureModerationPresenter)
            implementation(projects.featureModerationUi)
            implementation(projects.featureReportApi)
            implementation(projects.featureReportPresenter)
            implementation(projects.featureReportUi)
            // Feature modules, so the platform Metro graphs here aggregate their presenter and UI factories.
            implementation(projects.featureSearchApi)
            implementation(projects.featureSearchPresenter)
            implementation(projects.featureSearchUi)
            implementation(projects.uiDesignsystem)
            implementation(projects.uiResources)
            implementation(libs.circuit.foundation)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.serialization)
            implementation(libs.circuitx.gesture.navigation)
            implementation(libs.circuitx.subcircuit)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.vico.multiplatform)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            // Vulkan, like desktop off macOS and MapLibre Android's default. Tested on the arm64 emulator and a Pixel 9 Pro XL. It costs
            // about 3 MB more per ABI than OpenGL, mostly the glslang shader compiler (see BACKLOG.md).
            runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
        }
        // MapLibre Compose has no wasmJs target yet (maplibre/maplibre-compose#209, needs CMP 1.13), so the web app keeps the Canvas
        // RoutePreview (see ActivityMap and BACKLOG.md).
        // Desktop's native runtime is picked per OS in :desktopApp.
        nonWebMain.dependencies { implementation(libs.maplibre.compose) }
        // api so the framework can export it (see the framework block above).
        iosMain.dependencies { api(projects.coreActivityfile) }
        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
        jvmTest.dependencies { implementation(projects.appFixtures) }
    }
}

// ScreenshotCoverageTest reads :androidApp's screenshot sources and the feature UI modules' sources, so a removed screenshot file or a
// new feature UI reruns it.
tasks.named<Test>("jvmTest") {
    inputs.dir(isolated.rootProject.projectDirectory.dir("androidApp/src/screenshotTest")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .files(isolated.rootProject.projectDirectory.dir("feature").asFileTree.matching { include("*/ui/src/commonMain/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("featureUiSources")
}
