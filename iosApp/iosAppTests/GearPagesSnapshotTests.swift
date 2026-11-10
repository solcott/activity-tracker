import SwiftUI
import Testing

@testable import ActivityTracker

/// A gear collection and Ignored devices in each of their sample states, against their references (ui/settings-layout,
/// dev/ios-snapshot-tests). The samples hold no picture URLs, so pictures are their placeholders. The delete alert presents over the
/// page, so its sample shows the page under it; the alert's words are `GearPagesTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct GearPagesSnapshotTests {
    nonisolated private static let collectionSamples = ["loading", "failed", "loaded", "refreshing", "refreshFailed", "empty"]
    nonisolated private static let collectionVariants = ["deleting", "deleteFailed", "confirmingDelete"]
    nonisolated private static let ignoredSamples = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let ignoredVariants = ["nothingIgnored", "unignoreFailed"]

    @Test(arguments: collectionSamples + collectionVariants)
    func gearCollection(sample: String) throws {
        let state = try #require(Samples.gearCollection[sample] ?? Samples.gearCollectionVariants[sample])
        assertAppSnapshot(of: NavigationStack { GearCollectionView(state: state) }, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: ignoredSamples + ignoredVariants)
    func ignoredSensors(sample: String) throws {
        let state = try #require(Samples.ignoredSensors[sample] ?? Samples.ignoredSensorsVariants[sample])
        assertAppSnapshot(of: NavigationStack { IgnoredSensorsView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.gearCollection.keys) == Set(Self.collectionSamples))
        #expect(Set(Samples.gearCollectionVariants.keys) == Set(Self.collectionVariants))
        #expect(Set(Samples.ignoredSensors.keys) == Set(Self.ignoredSamples))
        #expect(Set(Samples.ignoredSensorsVariants.keys) == Set(Self.ignoredVariants))
    }
}
