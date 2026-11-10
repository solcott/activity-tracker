import Foundation
import IosShell
import Observation
import UIKit

/// Saving a finished data export from the native Account page (auth/data-export): Kotlin zips the archive into a file in the caches
/// folder, streamed rather than held in memory (`IosApp.writeArchive`), the system's share sheet offers it, and the file goes once the
/// sheet has closed. The sheet says only whether an activity (Save to Files, AirDrop…) ran, not whether the zip was kept, so after one
/// runs the page asks before the app's copy goes (`askingKeptShared`), once the sheet has gone: an alert can't show over it.
///
/// `ShareLink` isn't used: it doesn't say whether anything ran.
@Observable
@MainActor
final class ArchiveShare {
    /// Writes `archive` to the file at the path, replacing one there; whether it was written.
    typealias Write = @MainActor (_ archive: ExportArchive, _ path: String) async -> Bool
    /// Offers the file in the share sheet and returns once it has closed: whether an activity ran.
    typealias Present = @MainActor (_ file: URL) async -> Bool

    /// From Save until the sheet has closed and the file is gone: Save is off, and the kept question waits.
    private(set) var sharing = false

    private let write: Write
    private let present: Present
    private let folder: URL

    init(
        write: @escaping Write = ArchiveShare.writeWithKotlin,
        present: @escaping Present = ArchiveShare.presentShareSheet,
        folder: URL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
    ) {
        self.write = write
        self.present = present
        self.folder = folder
    }

    /// Writes and offers `archive`. What the page tells its presenter: shared once an activity ran, failed when the zip couldn't be
    /// written, and nothing when the sheet was dismissed, which keeps the export to save later.
    func share(_ archive: ExportArchive) async -> (any AccountEvent)? {
        guard !sharing else { return nil }
        sharing = true
        defer { sharing = false }
        let file = folder.appending(path: archive.name)
        guard await write(archive, file.path(percentEncoded: false)) else { return AccountEvent.ExportSaveFailed.shared }
        let ran = await present(file)
        // The sheet has copied it wherever it went; a later save writes it again.
        try? FileManager.default.removeItem(at: file)
        return ran ? AccountEvent.ExportShared.shared : nil
    }

    static func writeWithKotlin(_ archive: ExportArchive, _ path: String) async -> Bool {
        // Kotlin's classes aren't Sendable to Swift. The archive is immutable on Kotlin's side, and the write does its work on Kotlin's
        // IO dispatcher whichever thread calls it.
        nonisolated(unsafe) let archive = archive
        return (try? await IosApp.shared.writeArchive(archive: archive, path: path)) ?? false
    }

    static func presentShareSheet(_ file: URL) async -> Bool {
        guard let presenter = topViewController() else { return false }
        return await withCheckedContinuation { continuation in
            let sheet = UIActivityViewController(activityItems: [file], applicationActivities: nil)
            // Called as the sheet closes; once only, since a continuation resumes once.
            var resumed = false
            sheet.completionWithItemsHandler = { _, completed, _, _ in
                guard !resumed else { return }
                resumed = true
                continuation.resume(returning: completed)
            }
            // An iPad shows the sheet as a popover, which needs an anchor.
            if let popover = sheet.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            presenter.present(sheet, animated: true)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first {
            $0.activationState == .foregroundActive
        }
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
