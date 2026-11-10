package io.github.solcott.activitytracker.ios

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.HeldPhotoBytes
import io.github.solcott.activitytracker.app.IosAppHost
import io.github.solcott.activitytracker.app.ScreenViewController
import io.github.solcott.activitytracker.app.background.IosBackgroundUploads
import io.github.solcott.activitytracker.app.background.IosDataExports
import io.github.solcott.activitytracker.app.background.IosImportSaves
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.HostRecord
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.writeToPath
import kotlinx.io.bytestring.ByteString
import platform.UIKit.UIViewController

/**
 * Everything `iosApp`'s Swift calls in Kotlin. Swift export turns this into Swift, so a signature here uses only types it can carry: a
 * `UIViewController`, the presenter host's types, [FitDecoder] with its DTOs, a `ByteString`, a data export's archive, and plain values. A
 * type from a module that isn't exported (Compose, Circuit, Metro, `:composeApp` itself) would compile to a stub that stops the app when
 * called (dev/ios-swift-interop).
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

    /**
     * The bytes of the photo a `heldphoto:` URL names (one this device uploaded and the session holds, such as a non-public gear photo),
     * for the native image loader; null for any other URL, when signed out, or when the session doesn't hold it.
     */
    fun heldPhoto(url: String): ByteString? = HeldPhotoBytes(url)

    /**
     * Zips a finished data export into the file at [path], replacing one already there, for the native Account page to offer in the share
     * sheet (auth/data-export). Whether it was written; a failed write leaves no file. Swift awaits it.
     */
    suspend fun writeArchive(archive: ExportArchive, path: String): Boolean = archive.writeToPath(path)

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
