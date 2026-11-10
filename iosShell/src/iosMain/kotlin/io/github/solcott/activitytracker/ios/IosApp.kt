package io.github.solcott.activitytracker.ios

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.IosAppHost
import io.github.solcott.activitytracker.app.ScreenViewController
import io.github.solcott.activitytracker.app.background.IosBackgroundUploads
import io.github.solcott.activitytracker.app.background.IosDataExports
import io.github.solcott.activitytracker.app.background.IosImportSaves
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.HostRecord
import platform.UIKit.UIViewController

/**
 * Everything `iosApp`'s Swift calls in Kotlin. Swift export turns this into Swift, so a signature here uses only types it can carry: a
 * `UIViewController`, the presenter host's types, [FitDecoder] with its DTOs, and plain values. A type from a module that isn't exported
 * (Compose, Circuit, Metro, `:composeApp` itself) would compile to a stub that stops the app when called (dev/ios-swift-interop).
 *
 * Each function takes the app's `SwiftFitDecoder`, because whichever runs first creates the app's graph: the UI, or a background task when
 * iOS launches the app for one.
 */
object IosApp {
    /**
     * The app's screens, run with no UI (dev/presenter-host): which of sign-in and a signed-in session shows, each tab's stack, and every
     * screen's states. The SwiftUI shell follows it and draws each screen. Started by the first call.
     */
    fun host(fitDecoder: FitDecoder): AppHost = IosAppHost(fitDecoder)

    /** A screen drawn by the shared Compose UI, for one the shell has no native view for. Call [host] first. */
    fun screenViewController(record: HostRecord): UIViewController = ScreenViewController(record)

    /**
     * Registers the handlers of the app's background tasks: uploading queued writes (data/outbox), finishing batch saves
     * (feed/batch-import) and finishing data exports (auth/data-export). `iOSApp.swift` calls it from its initializer, since iOS takes
     * registrations only until the app finishes launching.
     */
    fun registerBackgroundTasks(fitDecoder: FitDecoder) {
        IosBackgroundUploads.register(fitDecoder)
        IosImportSaves.register(fitDecoder)
        IosDataExports.register(fitDecoder)
    }

    /** Asks iOS to run each background task later. `iOSApp.swift` calls it when the app goes to the background. */
    fun scheduleBackgroundTasks() {
        IosBackgroundUploads.schedule()
        IosImportSaves.schedule()
        IosDataExports.schedule()
    }
}
