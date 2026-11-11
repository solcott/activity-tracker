import SwiftUI
import Testing

@testable import ActivityTracker

/// The native profile in each of its sample states, against their references (graph/follow-requests, graph/blocks, graph/mutes,
/// dev/ios-snapshot-tests), on the samples' clock. The samples hold no picture URLs that load, so avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ProfileSnapshotTests {
    nonisolated private static let samples = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "self", "posts", "noPosts", "postsFailed",
    ]
    nonisolated private static let variants = [
        "following", "requested", "followPending", "muted", "blocked", "blockedOnBluesky", "ownApproving", "details", "labeled",
        "warned", "hidden", "actionFailed",
    ]

    @Test(arguments: samples + variants)
    func profile(sample: String) throws {
        let state = try #require(Samples.profile[sample] ?? Samples.profileVariants[sample])
        assertAppSnapshot(of: NavigationStack { ProfilePage(state: state) }.environment(\.clock, Samples.clock), named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.profile.keys) == Set(Self.samples))
        #expect(Set(Samples.profileVariants.keys) == Set(Self.variants))
    }
}
