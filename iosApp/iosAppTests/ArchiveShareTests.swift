import Foundation
import Testing

@testable import ActivityTracker

/// Saving an export from the native Account page (auth/data-export): what the page tells its presenter after the share sheet, and that
/// the app's file never outlives it. The sheet is a stand-in that says whether an activity ran; the writer writes a few bytes, as
/// Kotlin's would write the zip.
@MainActor
struct ArchiveShareTests {
    private let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)

    private var archive: ExportArchive {
        get throws { try #require(Samples.account["exportReady"]?.export as? DataExportStatus.Ready).archive }
    }

    private func writes(_ success: Bool) -> ArchiveShare.Write {
        { archive, path in
            guard success else { return false }
            try? FileManager.default.createDirectory(atPath: (path as NSString).deletingLastPathComponent, withIntermediateDirectories: true)
            return FileManager.default.createFile(atPath: path, contents: Data("zip".utf8))
        }
    }

    /// A sheet that sees the file, then closes with `ran`.
    private func sheet(_ ran: Bool, saw: @escaping (Bool) -> Void = { _ in }) -> ArchiveShare.Present {
        { file in
            saw(FileManager.default.fileExists(atPath: file.path(percentEncoded: false)))
            return ran
        }
    }

    private var file: URL { get throws { folder.appending(path: try archive.name) } }

    @Test func anActivityThatRanSaysShared() async throws {
        var offered = false
        let share = ArchiveShare(write: writes(true), present: sheet(true) { offered = $0 }, folder: folder)
        let event = await share.share(try archive)
        #expect(event is AccountEvent.ExportShared)
        #expect(offered, "the sheet is offered the written file")
        #expect(!FileManager.default.fileExists(atPath: try file.path(percentEncoded: false)))
        #expect(!share.sharing)
    }

    @Test func aDismissedSheetSaysNothing() async throws {
        let share = ArchiveShare(write: writes(true), present: sheet(false), folder: folder)
        #expect(await share.share(try archive) == nil)
        #expect(!FileManager.default.fileExists(atPath: try file.path(percentEncoded: false)))
    }

    @Test func aFailedWriteSaysSoAndOffersNothing() async throws {
        var presented = false
        let share = ArchiveShare(write: writes(false), present: { _ in presented = true; return true }, folder: folder)
        #expect(await share.share(try archive) is AccountEvent.ExportSaveFailed)
        #expect(!presented)
    }
}
