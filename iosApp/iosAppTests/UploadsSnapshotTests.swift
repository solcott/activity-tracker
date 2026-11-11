import SwiftUI
import Testing

@testable import ActivityTracker

/// The upload queue in each of its sample states, against its references (data/upload-queue, dev/ios-snapshot-tests), on the samples'
/// clock. The cancel questions present over the page, so their samples show the page under them; the questions' words are
/// `UploadsTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct UploadsSnapshotTests {
    nonisolated private static let samples = [
        "loading", "failed", "loaded", "refreshing", "refreshFailed", "empty", "ownOnly", "importPartWay", "importExpanded", "paused",
        "refused", "finished", "saving", "stopped", "confirmingCancelRestSaving", "confirmingCancel", "confirmingCancelRest",
        "cancelFailed", "sendingAgain",
    ]

    @Test(arguments: samples)
    func uploads(sample: String) throws {
        let state = try #require(Samples.uploads[sample])
        assertAppSnapshot(
            of: NavigationStack { UploadsView(state: state) }.environment(\.clock, Samples.clock), named: sample,
            precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.uploads.keys) == Set(Self.samples))
    }
}
