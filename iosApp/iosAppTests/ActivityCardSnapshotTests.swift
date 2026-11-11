import SwiftUI
import Testing

@testable import ActivityTracker

/// The native activity card in each case it draws differently, and a batch import's page in each of its states, against their
/// references (ui/ios-design-system, feed/import-cards, dev/ios-snapshot-tests), on the samples' clock.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ActivityCardSnapshotTests {
    nonisolated private static let cards = [
        "ride", "liked", "untitled", "group", "photos", "blurred", "own", "warned", "takenDown", "dive", "strength", "dateOnly", "broken",
        "longTitle",
    ]
    nonisolated private static let pages = [
        "loading", "failed", "loaded", "refreshing", "refreshFailed", "empty", "loadingMore", "pageFailed", "end",
    ]
    nonisolated private static let pageVariants = ["threeMonths", "likeFailed"]

    private static let cardSize = CGSize(width: 393, height: 560)

    private func cardList(_ name: String, revealed: Bool = false) throws -> some View {
        let activity = try #require(Samples.activityCards[name])
        return List {
            Section {
                ActivityCardView(
                    content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: Samples.cardViewer), revealed: revealed
                ) { _ in }
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.clock, Samples.clock)
    }

    @Test(arguments: cards)
    func card(sample: String) throws {
        assertAppSnapshot(of: try cardList(sample), size: Self.cardSize, named: sample)
    }

    @Test func revealedPhotos() throws {
        assertAppSnapshot(of: try cardList("blurred", revealed: true), size: Self.cardSize, named: "revealed")
    }

    @Test func atTheLargestTextSize() throws {
        assertAppSnapshot(
            of: try cardList("ride").environment(\.dynamicTypeSize, .accessibility3), size: CGSize(width: 393, height: 1_200),
            named: "accessibility3")
    }

    @Test(arguments: pages)
    func page(sample: String) throws {
        let state = try #require(Samples.importBatch[sample])
        assertAppSnapshot(of: NavigationStack { ImportBatchView(state: state) }.environment(\.clock, Samples.clock), named: sample)
    }

    @Test(arguments: pageVariants)
    func pageVariant(sample: String) throws {
        let state = try #require(Samples.importBatchVariants[sample])
        assertAppSnapshot(of: NavigationStack { ImportBatchView(state: state) }.environment(\.clock, Samples.clock), named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.activityCards.keys) == Set(Self.cards))
        #expect(Set(Samples.importBatch.keys) == Set(Self.pages))
        #expect(Set(Samples.importBatchVariants.keys) == Set(Self.pageVariants))
    }
}
