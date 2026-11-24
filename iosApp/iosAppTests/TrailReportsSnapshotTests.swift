import SwiftUI
import Testing

@testable import ActivityTracker

/// Trail reports' own screens against their references (segments/trail-reports, dev/ios-snapshot-tests), on the samples' clock: every
/// report on a segment in each loading state and a photo full screen; the Trail page's conditions are `InteractiveMapsSnapshotTests`'
/// "conditions" path sample. Snapshots run outside a session, so the photos draw their placeholders.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct TrailReportsSnapshotTests {
    nonisolated private static let reports = ["loading", "failed", "loaded"]

    @Test(arguments: reports)
    func trailReports(sample: String) throws {
        let state = try #require(Samples.trailReports[sample])
        let page = NavigationStack { TrailReportsView(state: state) }.environment(\.clock, Samples.clock)
        assertAppSnapshot(of: page, named: sample, precision: Snapshots.barButtons)
    }

    @Test func trailPhotos() {
        assertAppSnapshot(of: NavigationStack { TrailPhotosView(state: Samples.trailPhotos) }, named: "photo")
    }

}
