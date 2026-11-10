@file:OptIn(ExperimentalSwiftExportDsl::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.export.SwiftExportVisibility
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
    kotlin("multiplatform")
    // For its resources tasks alone: this module has no Compose code, but the plugin of the module Xcode's task runs in is what copies
    // every dependency's strings, drawables and fonts into the app. Without it the app builds without them. It refuses to apply
    // without the Compose compiler plugin.
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("activitytracker.quality")
    // checkSwiftExportStubs, and the list of contract modules this build exports.
    id("activitytracker.swift.export")
}

/** The Swift module the presenter host's types are generated into. */
val presenterHostSwiftModule = "PresenterHost"

// The one Kotlin binary iosApp links, exported to Swift with Swift export (dev/ios-swift-interop). It has no code: what it exports is its
// api dependencies. Xcode's build phase runs :iosExport:embedSwiftExportForXcode, then :iosExport:checkSwiftExportStubs.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    // A module named here is exported whole; one it reaches (:core-data, :core-activityfile, kotlinx-datetime) comes through only as far
    // as the named modules' signatures name its types, which keeps the generated Swift small and is why :iosShell is a separate module.
    //
    // Named: :iosShell, and every screen contract (swiftContracts.modules: the text field state, :app-common-api and each feature's API
    // module, read from the build's module list, so a new feature needs no line here).
    sourceSets {
        iosMain.dependencies {
            api(projects.iosShell)
            swiftContracts.modules.forEach { api(project(it.path)) }
        }
    }

    export {
        swift {
            moduleName = "ActivityTrackerKotlin"
            rootPackage = "io.github.solcott.activitytracker"

            xcodeIntegration {
                configure(projects.iosShell) {
                    moduleName = "IosShell"
                    rootPackage = "io.github.solcott.activitytracker.ios"
                }
                // Each contract module under a name from its path (:feature-login-api is FeatureLoginApi), which checkSwiftExportStubs
                // finds its generated Swift by. No root package: Swift spells a contract's types in full and aliases what it uses.
                swiftContracts.modules.forEach { contract ->
                    configure(dependencies.project(contract.path)) { moduleName = contract.swiftName }
                }
                // Only the name takes effect: a module exported through another's signatures gets no top-level names for its types,
                // whatever its root package, so Swift spells them in full (SwiftFitDecoder aliases the package).
                configure(projects.coreActivityfile) { moduleName = "ActivityFile" }
                // The presenter host (dev/presenter-host), reached through IosApp's signatures like :core-activityfile. It can't be
                // exported whole: it composes presenters, so its sources name Compose, which checkSwiftExportSources refuses in a
                // module that is. Not "AppHost", which is also the name of its main class.
                configure(projects.appHost) { moduleName = presenterHostSwiftModule }
                // kotlinx-datetime is left alone on purpose. DecodedActivity's constructor takes a LocalDateTime, and a signature naming
                // a hidden module's type is generated as a stub that stops the app, with no warning. Marking it EXPOSED instead would
                // export all of it, four times the Swift of what the signatures reach.

                // Hidden only when no contract signature names one of its types: a hidden module's type in a signature makes that
                // declaration a stub. :core-location (GeoPoint, RecordingState) and kotlinx-datetime are in states, so they stay
                // unconfigured, as do Ktor, Room and the rest of what :core-data's signatures reach.
                configure("org.jetbrains.kotlinx:kotlinx-serialization-core", SwiftExportVisibility.HIDDEN)
                // Exported whole, where "as far as named" brings only the ByteString class: Swift builds one from Data with the module's
                // NSData.toByteString(), and has no other way to (Kotlin's ByteArray initialiser is itself a stub). About 900 lines.
                configure("org.jetbrains.kotlinx:kotlinx-io-bytestring", SwiftExportVisibility.EXPOSED)
            }
        }
    }
}

// Xcode's build phase runs this after the export. A declaration whose signature names a type from a hidden module is generated with a
// body that stops the app, with no warning: in the contracts, the text field state and :iosShell that fails the build here.
tasks.named<CheckSwiftExportStubs>(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME) {
    failOn.add(swiftModuleName(projects.iosShell.path))
    // No source check covers the host (above), so this is what keeps a hidden module's type out of what Swift reads there.
    failOn.add(presenterHostSwiftModule)
    sentinelModule.set(swiftModuleName(projects.iosShell.path))
    // Not for a contract's own field or event: there, stop hiding the module or change the type (dev/ios-swift-interop).
    allowed.put(
        "serializer",
        "kotlinx-serialization's generated accessor on every @Serializable screen, typed with the hidden serialization-core's " +
            "KSerializer. Only Circuit's Kotlin side restores a screen; Swift has no use for a serializer.",
    )
}
