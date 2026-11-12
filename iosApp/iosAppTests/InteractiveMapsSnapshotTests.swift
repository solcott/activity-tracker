import SwiftUI
import Testing

@testable import ActivityTracker

/// The enlarged segment map, segment browse and Accept invite in each of their sample states against their references
/// (segments/segment-map-viewer, segments/segment-browse, feed/activity-invites, dev/ios-snapshot-tests). Snapshots run outside a session,
/// so every map draws its stand-in and loads no tiles (`\.liveMaps`).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct InteractiveMapsSnapshotTests {
    nonisolated private static let mapSamples = ["loading", "failed", "loaded", "refreshFailed", "credited", "agency"]
    nonisolated private static let browseSamples = [
        "loading", "failed", "loaded", "refreshFailed", "selected", "tooWide", "nameResults", "nothingHere", "nowhere", "locating",
    ]
    nonisolated private static let inviteSamples = ["loading", "failed", "empty", "loaded", "overlapping", "accepting", "signInAgain"]
    /// An 11-inch iPad on its side: wide enough for browse's list beside its map.
    private static let iPad = CGSize(width: 1_180, height: 820)
    private static let tall = CGSize(width: 393, height: 1_400)

    @Test(arguments: mapSamples)
    func segmentMap(sample: String) throws {
        let state = try #require(Samples.segmentMap[sample])
        assertAppSnapshot(of: NavigationStack { SegmentMapView(state: state) }, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: browseSamples)
    func segmentBrowse(sample: String) throws {
        let state = try #require(Samples.segmentBrowse[sample])
        assertAppSnapshot(of: browse(state), named: sample, precision: Snapshots.barButtons)
    }

    @Test func segmentBrowseOnAnIPad() throws {
        let state = try #require(Samples.segmentBrowse["selected"])
        assertAppSnapshot(of: browse(state), size: Self.iPad, named: "selected", precision: Snapshots.barButtons)
    }

    @Test(arguments: inviteSamples)
    func acceptInvite(sample: String) throws {
        let state = try #require(Samples.acceptInvite[sample])
        assertAppSnapshot(
            of: NavigationStack { AcceptInviteView(state: state) }, size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    /// In a stack with an inline title, as the Search tab holds it under its bar.
    private func browse(_ state: SegmentBrowseState) -> some View {
        NavigationStack {
            SegmentBrowseView(state: state, memory: SegmentsMapMemory()).navigationBarTitleDisplayMode(.inline)
        }
    }
}
