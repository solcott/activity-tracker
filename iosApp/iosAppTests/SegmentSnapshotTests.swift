import SwiftUI
import Testing

@testable import ActivityTracker

/// The segment page in each of its sample states against their references (segments/segment-efforts, segments/segment-elevation,
/// segments/segment-names, dev/ios-snapshot-tests), on the samples' clock. Snapshots run outside a session, so the map draws the line
/// preview and loads no tiles (`\.liveMaps`). Their words are `SegmentTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SegmentSnapshotTests {
    nonisolated private static let samples = [
        "loading", "failed", "loaded", "refreshing", "refreshFailed", "renamed", "votingUnavailable", "lift", "elevation",
        "elevationFromEfforts",
    ]
    nonisolated private static let variants = ["openStreetMap", "duplicate"]
    /// Tall enough for the whole page down to its efforts.
    private static let tall = CGSize(width: 393, height: 1_700)

    private func page(_ state: SegmentState) -> some View {
        NavigationStack { SegmentPage(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples)
    func segment(sample: String) throws {
        let state = try #require(Samples.segment[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: variants)
    func segmentVariant(sample: String) throws {
        let state = try #require(Samples.segmentVariants[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func imperialProfile() throws {
        let state = try #require(Samples.segmentVariants["openStreetMap"])
        assertAppSnapshot(
            of: page(state).environment(\.stats, Stats(units: .Imperial)), size: Self.tall, named: "imperial",
            precision: Snapshots.barButtons)
    }
}
