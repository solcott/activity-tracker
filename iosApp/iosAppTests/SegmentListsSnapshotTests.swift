import SwiftUI
import Testing

@testable import ActivityTracker

/// Starred segments, a segment's leaderboard and Mayors, and a dive site's Regulars in each of their sample states, against their
/// references (segments/segment-favorites, segments/segment-leaderboards, segments/segment-mayors, dive-sites/site-regulars,
/// dev/ios-snapshot-tests). The samples hold no picture URLs, so avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SegmentListsSnapshotTests {
    nonisolated private static let paged = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "loadingMore", "pageFailed", "end",
    ]
    nonisolated private static let starredVariants = ["unavailable", "unstarFailed", "needsSignIn"]
    nonisolated private static let leaderboardVariants = ["filtered", "firstPage"]
    nonisolated private static let mayorsVariants = ["filtered"]
    nonisolated private static let regularsVariants = ["lastYear"]

    @Test(arguments: paged + starredVariants)
    func starredSegments(sample: String) throws {
        let state = try #require(Samples.starredSegments[sample] ?? Samples.starredSegmentsVariants[sample])
        assertAppSnapshot(of: NavigationStack { StarredSegmentsView(state: state) }, named: sample)
    }

    @Test(arguments: paged + leaderboardVariants)
    func leaderboard(sample: String) throws {
        let state = try #require(Samples.segmentLeaderboard[sample] ?? Samples.segmentLeaderboardVariants[sample])
        assertAppSnapshot(of: NavigationStack { SegmentLeaderboardView(state: state) }, named: sample)
    }

    @Test func leaderboardInPounds() throws {
        let state = try #require(Samples.segmentLeaderboardVariants["filtered"])
        assertAppSnapshot(
            of: NavigationStack { SegmentLeaderboardView(state: state) }.environment(\.stats, Stats(units: .Imperial)), named: "imperial")
    }

    @Test(arguments: paged + mayorsVariants)
    func mayors(sample: String) throws {
        let state = try #require(Samples.segmentMayors[sample] ?? Samples.segmentMayorsVariants[sample])
        assertAppSnapshot(of: NavigationStack { SegmentMayorsView(state: state) }, named: sample)
    }

    @Test(arguments: paged + regularsVariants)
    func regulars(sample: String) throws {
        let state = try #require(Samples.diveSiteRegulars[sample] ?? Samples.diveSiteRegularsVariants[sample])
        assertAppSnapshot(of: NavigationStack { DiveSiteRegularsView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.starredSegments.keys) == Set(Self.paged))
        #expect(Set(Samples.starredSegmentsVariants.keys) == Set(Self.starredVariants))
        #expect(Set(Samples.segmentLeaderboard.keys) == Set(Self.paged))
        #expect(Set(Samples.segmentLeaderboardVariants.keys) == Set(Self.leaderboardVariants))
        #expect(Set(Samples.segmentMayors.keys) == Set(Self.paged))
        #expect(Set(Samples.segmentMayorsVariants.keys) == Set(Self.mayorsVariants))
        #expect(Set(Samples.diveSiteRegulars.keys) == Set(Self.paged))
        #expect(Set(Samples.diveSiteRegularsVariants.keys) == Set(Self.regularsVariants))
    }
}
