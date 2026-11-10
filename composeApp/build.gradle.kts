plugins {
    id("activitytracker.kmp.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    id("activitytracker.native.oauth")
}

val generateNetworkConfig =
    tasks.register<GenerateNetworkConfig>("generateNetworkConfig") {
        description = "Generate the local stack's network config for every client"
        packageName = "io.github.solcott.activitytracker.app"
        appviewUrl = nativeOAuth.appviewUrl
        appviewPublicUrl = nativeOAuth.appviewPublicUrl
        redirectScheme = nativeOAuth.redirectScheme
        labelerDid = nativeOAuth.labelerDid
        plcUrl = nativeOAuth.plcUrl
        handleResolverUrl = nativeOAuth.handleResolverUrl
        outputDir = layout.buildDirectory.dir("generated/networkConfig/kotlin")
    }

// The strings and drawables live in :ui-resources, whose Res keeps this module's old package, so this module must not generate its own.
compose.resources { generateResClass = never }

// UI tests (AppPreview and :app-fixtures' samples), and Skia for tests that encode images (PhotoCompressionTest).
composeUiTests { jvm = true }

kotlin {
    // SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain { kotlin.srcDir(generateNetworkConfig) }
        commonMain.dependencies {
            // GearChoices, which the record, import and edit presenters inject.
            implementation(projects.appCommonPresenter)
            implementation(projects.appCommonUi)
            implementation(projects.coreData)
            implementation(projects.featureDetailApi)
            implementation(projects.featureDetailPresenter)
            implementation(projects.featureDetailUi)
            implementation(projects.featureDivesiteApi)
            implementation(projects.featureDivesitePresenter)
            implementation(projects.featureDivesiteUi)
            implementation(projects.featureEditApi)
            implementation(projects.featureEditPresenter)
            implementation(projects.featureEditUi)
            implementation(projects.featureFeedApi)
            implementation(projects.featureFeedPresenter)
            implementation(projects.featureFeedUi)
            implementation(projects.featureFeedfilterApi)
            implementation(projects.featureFeedfilterPresenter)
            implementation(projects.featureFeedfilterUi)
            implementation(projects.featureGearApi)
            implementation(projects.featureGearPresenter)
            implementation(projects.featureGearUi)
            implementation(projects.featureInvitesApi)
            implementation(projects.featureInvitesPresenter)
            implementation(projects.featureInvitesUi)
            implementation(projects.featureLoginApi)
            implementation(projects.featureLoginPresenter)
            implementation(projects.featureLoginUi)
            implementation(projects.featureMapApi)
            implementation(projects.featureMapPresenter)
            implementation(projects.featureMapUi)
            implementation(projects.featureMediaApi)
            implementation(projects.featureMediaPresenter)
            implementation(projects.featureMediaUi)
            implementation(projects.featureModerationApi)
            implementation(projects.featureModerationPresenter)
            implementation(projects.featureModerationUi)
            implementation(projects.featurePostApi)
            implementation(projects.featurePostPresenter)
            implementation(projects.featurePostUi)
            implementation(projects.featureProfileApi)
            implementation(projects.featureProfilePresenter)
            implementation(projects.featureProfileUi)
            implementation(projects.featureRecordApi)
            implementation(projects.featureRecordPresenter)
            implementation(projects.featureRecordUi)
            implementation(projects.featureReportApi)
            implementation(projects.featureReportPresenter)
            implementation(projects.featureReportUi)
            // Feature modules, so the platform Metro graphs here aggregate their presenter and UI factories.
            implementation(projects.featureSearchApi)
            implementation(projects.featureSearchPresenter)
            implementation(projects.featureSearchUi)
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSegmentPresenter)
            implementation(projects.featureSegmentUi)
            implementation(projects.featureSettingsApi)
            implementation(projects.featureSettingsPresenter)
            implementation(projects.featureSettingsUi)
            implementation(projects.featureUploadsApi)
            implementation(projects.featureUploadsPresenter)
            implementation(projects.featureUploadsUi)
            implementation(projects.uiDesignsystem)
            implementation(projects.uiResources)
            implementation(libs.calf.ui)
            implementation(libs.circuit.foundation)
            implementation(libs.circuit.runtime)
            implementation(libs.circuit.serialization)
            implementation(libs.circuitx.gesture.navigation)
            implementation(libs.circuitx.subcircuit)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.vico.multiplatform)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.maplibre.compose)
            // Vulkan, like desktop off macOS and MapLibre Android's default. Tested on the arm64 emulator and a Pixel 9 Pro XL. It costs
            // about 3 MB more per ABI than OpenGL, mostly the glslang shader compiler (see BACKLOG.md).
            runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
        }
        // MapLibre Compose has no wasmJs target yet (maplibre/maplibre-compose#209, needs CMP 1.13), so the web app keeps the Canvas
        // RoutePreview (see ActivityMap and BACKLOG.md). On each non-web target rather than nonWebMain: this module has no nonWebMain
        // sources, so the convention doesn't declare that group here.
        // Desktop's native runtime is picked per OS in :desktopApp.
        iosMain.dependencies {
            // FitDecoder, which iosApp's Swift implements and hands to the iOS graph (see :iosShell).
            implementation(projects.coreActivityfile)
            implementation(libs.maplibre.compose)
        }
        jvmMain.dependencies { implementation(libs.maplibre.compose) }
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
