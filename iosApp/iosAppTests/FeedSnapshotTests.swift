import SwiftUI
import Testing

@testable import ActivityTracker

/// The native feed in each of its sample states, against their references (feed/feed-filter, feed/import-cards, data/outbox,
/// dev/ios-snapshot-tests), on the samples' clock, with an inline title: in a snapshot's
/// window a large title is drawn against the window's edge, with none of the margin it has on a device. No host runs in a snapshot, so the filter
/// bar is drawn from its own sample (at the default, or narrowed for a narrowed feed). The samples hold no picture URLs that load, so
/// avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct FeedSnapshotTests {
    nonisolated private static let samples = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "unsent", "paused", "imported", "posts", "reposted",
        "quotes", "askingGroups",
    ]
    nonisolated private static let variants = [
        "mine", "emptyFiltered", "emptyManualOnly", "askingSegments", "actionFailed", "pausedTomorrow",
    ]

    @Test(arguments: samples + variants)
    func feed(sample: String) throws {
        let state = try #require(Samples.feed[sample] ?? Samples.feedVariants[sample])
        assertAppSnapshot(
            of: NavigationStack {
                FeedPage(state: state, filterSample: Samples.feedFilterBar(for: state))
            }
            .environment(\.clock, Samples.clock),
            named: sample, precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.feed.keys) == Set(Self.samples))
        #expect(Set(Samples.feedVariants.keys) == Set(Self.variants))
    }
}
