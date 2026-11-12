import SwiftUI
import Testing

@testable import ActivityTracker

/// The dive site page in each of its sample states against their references (dive-sites/site-records, dive-sites/site-names,
/// dev/ios-snapshot-tests), on the samples' clock. Snapshots run outside a session, so the map draws the site's outline as the line
/// preview and loads no tiles (`\.liveMaps`); the samples hold no picture URLs, so pictures are their placeholders. Their words are
/// `DiveSiteTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct DiveSiteSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "refreshing", "refreshFailed", "proposed"]
    nonisolated private static let variants = ["renamed", "votingUnavailable", "duplicate"]
    /// Tall enough for the whole page down to its cards.
    private static let tall = CGSize(width: 393, height: 2_000)

    private func page(_ state: DiveSiteState) -> some View {
        NavigationStack { DiveSitePage(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples)
    func diveSite(sample: String) throws {
        let state = try #require(Samples.diveSite[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: variants)
    func diveSiteVariant(sample: String) throws {
        let state = try #require(Samples.diveSiteVariants[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func routeMapStandIn() throws {
        let map = try #require(Samples.diveSite["loaded"]?.routeMap)
        assertAppSnapshot(of: RouteMapView(map: map, expand: {}), size: CGSize(width: 393, height: 240), named: "expandable")
    }
}
