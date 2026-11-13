import SwiftUI
import Testing

@testable import ActivityTracker

/// The import screen in each of its sample states, its opened batch row and its zone sheet, against their references (feed/batch-import,
/// feed/import-duplicates, dev/ios-snapshot-tests), each recorded tall enough for the form under the preview; one built page at an iPad's
/// width, the preview beside the form. Outside a session the map draws its sketch. The opened row is a sheet over the batch, so it is
/// recorded on its own.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ImportSnapshotTests {
    nonisolated private static let samples = [
        "loaded", "reading", "readingProgress", "batch", "batchExport", "batchDuplicates", "batchSaving", "batchSaved", "batchSaveFailed",
        "savingImport", "stoppedImport", "duplicateCheckFailed", "preview", "newDevices", "afterImport",
    ]
    nonisolated private static let variants = [
        "page", "diveZone", "duplicate", "duplicateUploading", "duplicateChecking", "addsRecording", "tooFar", "saving", "saveFailed",
        "loadFailed", "batchPreview", "batchChecking", "savedSuggestions", "pastApplying",
    ]

    /// The tallest a 3x image can be (8,192 pixels); taller draws blank.
    private static let tall = CGSize(width: 393, height: 2_700)
    /// An 11-inch iPad on its side: wide enough for the preview beside the form.
    private static let iPad = CGSize(width: 1_180, height: 2_400)

    private func screen(_ state: ImportActivityState) -> some View {
        NavigationStack { ImportActivityView(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples + variants)
    func importActivity(sample: String) throws {
        let state = try #require(Samples.importActivity[sample] ?? Samples.importActivityVariants[sample])
        assertAppSnapshot(of: screen(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func wide() throws {
        let state = try #require(Samples.importActivityVariants["page"])
        assertAppSnapshot(
            of: screen(state).environment(\.horizontalSizeClass, .regular), size: Self.iPad, named: "iPad", precision: Snapshots.barButtons,
            keepsSafeArea: true)
    }

    @Test func openedRow() throws {
        let state = try #require(Samples.importActivityVariants["batchPreview"])
        let item = try #require(state.batch?.preview)
        assertAppSnapshot(
            of: ImportItemSheet(state: state, item: item).environment(\.clock, Samples.clock), size: CGSize(width: 393, height: 1_400),
            named: "batchPreview", precision: Snapshots.barButtons)
    }

    @Test(arguments: ["", "utc+8"])
    func zoneSheet(query: String) throws {
        let clock = try #require(Samples.importActivityVariants["diveZone"]?.preview?.clock)
        assertAppSnapshot(
            of: TimeZoneSheet(clock: clock, query: query) { _ in }, size: CGSize(width: 393, height: 500),
            named: query.isEmpty ? "all" : "utc8", precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.importActivity.keys) == Set(Self.samples))
        #expect(Set(Samples.importActivityVariants.keys) == Set(Self.variants))
    }
}
