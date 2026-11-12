import SwiftUI
import Testing

@testable import ActivityTracker

/// Moderation in each of its sample states against their references (moderation/private-subjects, dev/ios-snapshot-tests), each a
/// phone's screen in its stack's bar.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ModerationSnapshotTests {
    nonisolated private static let samples = ["loaded", "activity"]
    nonisolated private static let variants = ["comment", "commentNoName", "notUnderReview", "opening", "invalidUri", "openFailed"]

    @Test(arguments: samples + variants)
    func moderation(sample: String) throws {
        let state = try #require(Samples.moderation[sample] ?? Samples.moderationVariants[sample])
        assertAppSnapshot(of: NavigationStack { ModerationView(state: state) }, named: sample, precision: Snapshots.barButtons)
    }

    @Test func theSuiteCoversEverySample() {
        #expect(Set(Samples.moderation.keys) == Set(Self.samples))
        #expect(Set(Samples.moderationVariants.keys) == Set(Self.variants))
    }
}
