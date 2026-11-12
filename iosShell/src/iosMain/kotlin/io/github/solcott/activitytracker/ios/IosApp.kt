package io.github.solcott.activitytracker.ios

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.app.HeldPhotoBytes
import io.github.solcott.activitytracker.app.IosAppHost
import io.github.solcott.activitytracker.app.ScreenViewController
import io.github.solcott.activitytracker.app.background.IosBackgroundUploads
import io.github.solcott.activitytracker.app.background.IosDataExports
import io.github.solcott.activitytracker.app.background.IosImportSaves
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.HostRecord
import io.github.solcott.activitytracker.app.profile.EditProfileLimits
import io.github.solcott.activitytracker.app.ui.compressAvatar as compressForAvatar
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.writeToPath
import io.github.solcott.activitytracker.data.importsave.PickedFileSource
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.bytestring.ByteString
import platform.Foundation.NSURL
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
     * The files the native import screen's document picker gave, by their URLs (`file:` and security scoped), as the sources the import
     * reads a range at a time (feed/batch-import): the same `PickedFileSource` FileKit's picker gives the shared screen, which opens each
     * URL's security scope while it reads and which a batch save keeps as a bookmark. Swift sends them in
     * `ImportActivityEvent.FilesPicked`. A URL that doesn't parse is left out.
     */
    fun importSources(urls: List<String>): List<ImportSource> =
        urls.mapNotNull { NSURL.URLWithString(it) }.map { PickedFileSource(PlatformFile(it)) }

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

    /**
     * Writes an activity's exported file into the file at [path], replacing one already there, for the native activity page to offer in the
     * share sheet (feed/activity-export). Whether it was written. Swift awaits it.
     */
    suspend fun writeExport(file: ExportedFile, path: String): Boolean = file.writeToPath(path)

    /**
     * A profile photo the native edit profile picked, ready to attach: [image] (any format the platform decodes, a HEIC included) as a JPEG
     * scaled and re-encoded by the same `compressAvatar` the shared UI's pick runs, at [EditProfileLimits]' size and quality, so the photo
     * saved is the same on every platform. Null when [image] can't be read as a picture. Swift awaits it and sends the result in
     * `EditProfileEvent.AvatarPicked`.
     */
    suspend fun compressAvatar(image: ByteString): ByteString? = runSuspendCatching {
        ByteString(compressForAvatar(image.toByteArray(), EditProfileLimits.avatarPixels, EditProfileLimits.avatarQuality))
    }
        .getOrNull()

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
