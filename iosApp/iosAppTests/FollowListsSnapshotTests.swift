import SwiftUI
import Testing

@testable import ActivityTracker

/// The follow lists and follow requests in each of their sample states, against their references (graph/follow-requests,
/// dev/ios-snapshot-tests). The samples hold no picture URLs, so avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct FollowListsSnapshotTests {
    nonisolated private static let listSamples = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "loadingMore", "pageFailed", "end",
    ]
    nonisolated private static let listVariants = ["following", "followingEmpty", "removable"]
    nonisolated private static let requestSamples = ["loading", "failed", "empty", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let requestVariants = ["declined", "declinedEmpty", "answering", "needsSignIn", "truncated"]

    @Test(arguments: listSamples + listVariants)
    func followList(sample: String) throws {
        let state = try #require(Samples.followList[sample] ?? Samples.followListVariants[sample])
        assertAppSnapshot(of: NavigationStack { FollowListView(state: state) }, named: sample)
    }

    @Test(arguments: requestSamples + requestVariants)
    func followRequests(sample: String) throws {
        let state = try #require(Samples.followRequests[sample] ?? Samples.followRequestsVariants[sample])
        assertAppSnapshot(of: NavigationStack { FollowRequestsView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.followList.keys) == Set(Self.listSamples))
        #expect(Set(Samples.followListVariants.keys) == Set(Self.listVariants))
        #expect(Set(Samples.followRequests.keys) == Set(Self.requestSamples))
        #expect(Set(Samples.followRequestsVariants.keys) == Set(Self.requestVariants))
    }
}
