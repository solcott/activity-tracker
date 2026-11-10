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
}

// The one Kotlin binary iosApp links, exported to Swift with Swift export (dev/ios-swift-interop). It has no code: what it exports is its
// api dependency. Xcode's build phase runs :iosExport:embedSwiftExportForXcode.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    // :iosShell alone. A module named here is exported whole; one :iosShell reaches (:core-activityfile, kotlinx-datetime) comes through
    // only as far as :iosShell's signatures name its types, which keeps the generated Swift small and is why :iosShell is a separate
    // module.
    sourceSets { iosMain.dependencies { api(projects.iosShell) } }

    export {
        swift {
            moduleName = "ActivityTrackerKotlin"
            rootPackage = "io.github.solcott.activitytracker"

            xcodeIntegration {
                configure(projects.iosShell) {
                    moduleName = "IosShell"
                    rootPackage = "io.github.solcott.activitytracker.ios"
                }
                // Only the name takes effect: a module exported through another's signatures gets no top-level names for its types,
                // whatever its root package, so Swift spells them in full (SwiftFitDecoder aliases the package).
                configure(projects.coreActivityfile) { moduleName = "ActivityFile" }
                // kotlinx-datetime is left alone on purpose. DecodedActivity's constructor takes a LocalDateTime, and a signature naming
                // a hidden module's type is generated as a stub that stops the app, with no warning. Marking it EXPOSED instead would
                // export all of it, four times the Swift of what the signatures reach.

                // No signature Swift calls names a type of these: :core-activityfile's other api dependency, and the serializers
                // kotlinx-datetime's classes carry.
                configure(projects.coreLocation, SwiftExportVisibility.HIDDEN)
                configure("org.jetbrains.kotlinx:kotlinx-serialization-core", SwiftExportVisibility.HIDDEN)
            }
        }
    }
}
