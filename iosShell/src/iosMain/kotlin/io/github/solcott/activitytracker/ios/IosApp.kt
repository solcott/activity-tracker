package io.github.solcott.activitytracker.ios

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.HeldPhotoBytes
import io.github.solcott.activitytracker.app.IosAppHost
import io.github.solcott.activitytracker.app.ScreenViewController
import io.github.solcott.activitytracker.app.SegmentBrowseViewController
import io.github.solcott.activitytracker.app.background.IosBackgroundUploads
import io.github.solcott.activitytracker.app.background.IosDataExports
import io.github.solcott.activitytracker.app.background.IosImportSaves
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.host.AppHost
import io.github.solcott.activitytracker.app.host.HostRecord
import io.github.solcott.activitytracker.app.profile.EditProfileLimits
import io.github.solcott.activitytracker.app.ui.compressAvatar as compressForAvatar
import io.github.solcott.activitytracker.app.ui.compressPhoto as compressForUpload
import io.github.solcott.activitytracker.atproto.runSuspendCatching
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

    /**
     * A photo a native form picked, ready to attach: [image] (any format the platform decodes) re-encoded, scaled and stripped of its
     * metadata by the same `compressPhoto` the shared UI's pick runs, so the photo saved is the same on every platform. Null when [image]
     * can't be read as a picture. Swift awaits it and sends the result in the form's own event (`EditGearEvent.PhotoPicked`).
     */
    suspend fun compressPhoto(image: ByteString): PickedPhoto? = runSuspendCatching {
        compressForUpload(image.toByteArray())
    }
        .map { PickedPhoto(ByteString(it.bytes), it.mimeType, it.width, it.height) }
        .getOrNull()

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
     * The shared segment browse screen, for the native Search page's Segments mode until segment browse is native: what it opens goes on
     * the Search tab's stack through [record], the tab's root. Call [host] first.
     */
    fun segmentBrowseViewController(record: HostRecord): UIViewController = SegmentBrowseViewController(record)

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
