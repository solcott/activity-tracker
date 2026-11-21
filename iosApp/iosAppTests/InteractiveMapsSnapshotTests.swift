import SwiftUI
import Testing

@testable import ActivityTracker

/// The enlarged segment map, segment and dive site browse, the Trail page and Accept invite in each of their sample states against their references
/// (segments/segment-map-viewer, segments/segment-browse, segments/path-page, dive-sites/site-browse, feed/activity-invites, dev/ios-snapshot-tests). Snapshots run outside a session,
/// so every map draws its stand-in and loads no tiles (`\.liveMaps`).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct InteractiveMapsSnapshotTests {
    nonisolated private static let mapSamples = ["loading", "failed", "loaded", "refreshFailed", "credited", "agency"]
    nonisolated private static let browseSamples = [
        "loading", "failed", "loaded", "refreshFailed", "selected", "tooWide", "nameResults", "nothingHere", "nowhere", "locating",
    ]
    /// Segment browse also lists a segment group's segments as one row (segments/segment-groups); dive sites have no segment groups.
    nonisolated private static let segmentBrowseSamples = browseSamples + ["groupResults"]
    /// The Trail page (segments/path-page).
    nonisolated private static let pathSamples = [
        "loading", "failed", "loaded", "refreshFailed", "selected", "ride", "empty", "oneWay", "sections", "sectionsCollapsed", "trail",
        "trailExpanded",
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

    @Test(arguments: segmentBrowseSamples)
    func segmentBrowse(sample: String) throws {
        let state = try #require(Samples.segmentBrowse[sample])
        assertAppSnapshot(of: browse(state), named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: pathSamples)
    func path(sample: String) throws {
        let state = try #require(Samples.path[sample])
        assertAppSnapshot(of: NavigationStack { PathPage(state: state) }, named: sample, precision: Snapshots.barButtons)
    }

    @Test func pathOnAnIPad() throws {
        let state = try #require(Samples.path["selected"])
        assertAppSnapshot(
            of: NavigationStack { PathPage(state: state) }, size: Self.iPad, named: "selected", precision: Snapshots.barButtons)
    }

    @Test func segmentBrowseOnAnIPad() throws {
        let state = try #require(Samples.segmentBrowse["selected"])
        assertAppSnapshot(of: browse(state), size: Self.iPad, named: "selected", precision: Snapshots.barButtons)
    }

    @Test(arguments: browseSamples)
    func diveSiteBrowse(sample: String) throws {
        let state = try #require(Samples.diveSiteBrowse[sample])
        assertAppSnapshot(of: sitesBrowse(state), named: sample, precision: Snapshots.barButtons)
    }

    @Test func diveSiteBrowseOnAnIPad() throws {
        let state = try #require(Samples.diveSiteBrowse["selected"])
        assertAppSnapshot(of: sitesBrowse(state), size: Self.iPad, named: "selected", precision: Snapshots.barButtons)
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

    /// Dive site browse, held the same way (dive-sites/site-browse).
    private func sitesBrowse(_ state: DiveSiteBrowseState) -> some View {
        NavigationStack {
            DiveSiteBrowseView(state: state, memory: SegmentsMapMemory()).navigationBarTitleDisplayMode(.inline)
        }
    }
}
