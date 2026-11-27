import SwiftUI
import Testing

@testable import ActivityTracker

/// The trim screen in each of its sample states against their references (feed/activity-trim, dev/ios-snapshot-tests): recorded tall
/// enough for the whole form; outside a session its map is the line preview, the cut drawn as the route and the kept part over it.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct TrimSnapshotTests {
    nonisolated private static let samples = [
        "loading", "failed", "loaded", "trimmed", "byDistance", "indoor", "manual", "confirming", "exactValue", "draft", "notSent",
        "saveFailed",
    ]

    private static let size = CGSize(width: 393, height: 1_400)

    @Test(arguments: samples)
    func trim(sample: String) throws {
        let state = try #require(Samples.trim[sample])
        assertAppSnapshot(
            of: NavigationStack { TrimActivityView(state: state) }, size: Self.size, named: sample, precision: Snapshots.barButtons)
    }

    @Test func theSuiteCoversEverySample() {
        #expect(Set(Samples.trim.keys) == Set(Self.samples))
    }
}
