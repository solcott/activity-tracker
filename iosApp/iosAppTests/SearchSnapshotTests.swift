import SwiftUI
import Testing

@testable import ActivityTracker

/// The Search tab's root and the choice after leaving on another device in each of their sample states, against their references
/// (segments/segment-browse, auth/leaving, dev/ios-snapshot-tests). The search samples hold no picture URLs, so avatars are their
/// initials; Segments mode has no host record to run the shared browse screen, so only the choice and the bar are drawn over it.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SearchSnapshotTests {
    nonisolated private static let searchSamples = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "loadingMore", "pageFailed", "end",
    ]
    nonisolated private static let searchVariants = ["blank", "noMatch", "segments"]
    nonisolated private static let leftElsewhereSamples = ["loaded", "checking", "everything", "rejoining", "rejoinFailed"]

    @Test(arguments: searchSamples + searchVariants)
    func athleteSearch(sample: String) throws {
        let state = try #require(Samples.athleteSearch[sample] ?? Samples.athleteSearchVariants[sample])
        assertAppSnapshot(of: NavigationStack { AthleteSearchView(state: state).environment(\.tabRoot, true) }, named: sample)
    }

    @Test(arguments: leftElsewhereSamples)
    func leftElsewhere(sample: String) throws {
        let state = try #require(Samples.leftElsewhere[sample])
        assertAppSnapshot(of: NavigationStack { LeftElsewhereView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.athleteSearch.keys) == Set(Self.searchSamples))
        #expect(Set(Samples.athleteSearchVariants.keys) == Set(Self.searchVariants))
        #expect(Set(Samples.leftElsewhere.keys) == Set(Self.leftElsewhereSamples))
    }
}
