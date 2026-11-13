plugins {
    id("activitytracker.kmp.library")
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

// What every app's graph shares, with no UI: the app and session graphs' common views, the network config and the background passes. The
// Compose apps (:composeApp, :desktopApp, :webApp) add their Circuits to it. iOS code holds the iOS app's graph and host, which bind no UI
// factory: nothing here or below it draws (ui/ios-native-shell).
kotlin {
    // SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
    compilerOptions { optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi") }
    sourceSets {
        commonMain { kotlin.srcDir(generateNetworkConfig) }
        commonMain.dependencies {
            // api: AppGraph and SessionGraph name the host's types (the requests, the session runner) and :core-data's, which the app
            // shells that declare or use a graph then see.
            api(projects.appHost)
            api(projects.coreData)
            api(libs.circuitx.subcircuit)
            // @Stable on the graphs, which the Compose app takes as params.
            implementation(libs.compose.runtime)
        }
        iosMain.dependencies {
            // FitDecoder, which iosApp's Swift implements and hands to the iOS graph (see :iosShell).
            api(projects.coreActivityfile)

            // GearChoices, which the record, import and edit presenters inject.
            implementation(projects.appCommonPresenter)
            implementation(projects.coreLocation)
            // Every feature's presenters, so the iOS graph aggregates their factories. No UI module: the iOS graph binds none.
            implementation(projects.featureDetailPresenter)
            implementation(projects.featureDivesitePresenter)
            implementation(projects.featureEditPresenter)
            implementation(projects.featureFeedPresenter)
            implementation(projects.featureFeedfilterPresenter)
            implementation(projects.featureGearPresenter)
            implementation(projects.featureInvitesPresenter)
            implementation(projects.featureLoginPresenter)
            implementation(projects.featureMapPresenter)
            implementation(projects.featureMediaPresenter)
            implementation(projects.featureModerationPresenter)
            implementation(projects.featurePostPresenter)
            implementation(projects.featureProfilePresenter)
            implementation(projects.featureRecordPresenter)
            implementation(projects.featureReportPresenter)
            implementation(projects.featureSearchPresenter)
            implementation(projects.featureSegmentPresenter)
            implementation(projects.featureSettingsPresenter)
            implementation(projects.featureUploadsPresenter)
        }
    }
}
