import SwiftUI
import Testing

@testable import ActivityTracker

/// The gear and collection forms in each of their sample states, and their sheets' lists, against their references (ui/settings-layout,
/// dev/ios-snapshot-tests). Each form is recorded tall enough for all of it. The samples hold no reachable picture URL, so a saved photo
/// is its placeholder. The delete alert, the sheets and the calendar present over the form, so the forms' samples show the form under
/// them; their words are `GearEditorsTextTests`', and the sheets' lists are recorded on their own.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct GearEditorsSnapshotTests {
    nonisolated private static let gearSamples = ["loading", "failed", "loaded", "invalid", "new"]
    nonisolated private static let gearVariants = ["mountedRetired", "imperial", "saving", "saveFailed", "confirmingDelete"]
    nonisolated private static let collectionSamples = ["loading", "failed", "loaded", "invalid", "new"]
    nonisolated private static let collectionVariants = ["full", "nothingToAdd", "saving", "saveFailed"]

    private static let gearSize = CGSize(width: 393, height: 1_850)
    private static let collectionSize = CGSize(width: 393, height: 820)

    @Test(arguments: gearSamples + gearVariants)
    func editGear(sample: String) throws {
        let state = try #require(Samples.editGear[sample] ?? Samples.editGearVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { EditGearView(state: state) }, size: Self.gearSize, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: collectionSamples + collectionVariants)
    func editGearCollection(sample: String) throws {
        let state = try #require(Samples.editGearCollection[sample] ?? Samples.editGearCollectionVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { EditGearCollectionView(state: state) }, size: Self.collectionSize, named: sample,
            precision: Snapshots.barButtons)
    }

    @Test(arguments: ["sports", "defaultFor"])
    func typeChoiceSheet(choice: String) throws {
        let state = try #require(Samples.editGear["loaded"])
        assertAppSnapshot(
            of: TypeChoiceSheet(choice: choice == "sports" ? .sports : .defaultFor, state: state), named: choice,
            precision: Snapshots.barButtons)
    }

    @Test(arguments: ["loaded", "nothingToAdd"])
    func addItems(sample: String) throws {
        let state = try #require(Samples.editGearCollection[sample] ?? Samples.editGearCollectionVariants[sample])
        assertAppSnapshot(
            of: AddItemsSheet(others: state.others.elements(as: GearRow.self)) { _ in }, size: CGSize(width: 393, height: 420),
            named: sample, precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.editGear.keys) == Set(Self.gearSamples))
        #expect(Set(Samples.editGearVariants.keys) == Set(Self.gearVariants))
        #expect(Set(Samples.editGearCollection.keys) == Set(Self.collectionSamples))
        #expect(Set(Samples.editGearCollectionVariants.keys) == Set(Self.collectionVariants))
    }
}
