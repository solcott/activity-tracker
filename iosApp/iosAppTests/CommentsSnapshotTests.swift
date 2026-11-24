import SwiftUI
import Testing

@testable import ActivityTracker

/// The Comments screen in each of its states, its field at the foot, against their references (feed/comments, dev/ios-snapshot-tests).
/// `reportable` has a labeler configured, so others' comments show Report in their menus.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct CommentsSnapshotTests {
    nonisolated private static let screens = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let variants = [
        "empty", "ownHidden", "replying", "moreReplies", "commentsFailed", "sendFailed", "reportable",
    ]

    @Test(arguments: screens + variants)
    func comments(sample: String) throws {
        let state = try #require(Samples.comments[sample] ?? Samples.commentsVariants[sample])
        assertAppSnapshot(of: NavigationStack { CommentsView(state: state) }.environment(\.clock, Samples.clock), named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.comments.keys) == Set(Self.screens))
        #expect(Set(Samples.commentsVariants.keys) == Set(Self.variants))
    }
}
