import Foundation
import Testing

@testable import ActivityTracker

/// What the gear and collection forms say, limit and offer (ui/settings-layout, dev/shared-display-text). The rules are Kotlin's, with
/// their own tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared forms do.
@MainActor
struct GearEditorsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func aNewItemIsAddGearAtOnlyMe() throws {
        let state = try #require(Samples.editGear["new"])
        #expect(read(state.title) == "Add gear")
        #expect(state.name.value == "")
        #expect(!state.showsManagement)
        #expect(read(LabelText.shared.visibility(visibility: try #require(Samples.editGear["loaded"]).visibility)) == "Public")
    }

    @Test func aSavedItemIsEditGearWithItsChoices() throws {
        let state = try #require(Samples.editGear["loaded"])
        #expect(read(state.title) == "Edit gear")
        #expect(read(state.kindLabel) == "None")
        #expect(read(state.sportsLabel) == "Ride")
        #expect(read(state.defaultForLabel) == "No activity types")
        #expect(read(state.startingDistanceLabel) == "Starting distance (km)")
        #expect(read(state.photoAction) == "Add photo")
        #expect(state.showsKind)
        #expect(state.showsManagement)
        #expect(state.enabled)
        #expect(state.isForSport(sport: "ride"))
        #expect(!state.isForSport(sport: "run"))
        let kinds = state.kindOptions.elements(as: GearKindOption.self)
        #expect(kinds.first?.kind == nil)
        #expect(kinds.map { read($0.label) }.prefix(3) == ["None", "Road", "Gravel"])
        #expect(state.visibilityOptions.elements(as: String.self).count == 4)
        let collections = state.collections.elements(as: GearCollectionChoice.self)
        #expect(collections.map(\.name) == ["Race kit", "Winter"])
        #expect(collections.map(\.member) == [true, false])
    }

    @Test func noNameIsTheSharedError() throws {
        let state = try #require(Samples.editGear["invalid"])
        #expect(state.nameMissing)
        #expect(String(localized: .gearNameRequired) == "Give your gear a name")
    }

    @Test func imperialUnitsLabelTheStartInMiles() throws {
        #expect(read(try #require(Samples.editGearVariants["imperial"]).startingDistanceLabel) == "Starting distance (mi)")
    }

    @Test func aMountedRetiredChainSaysSo() throws {
        let state = try #require(Samples.editGearVariants["mountedRetired"])
        #expect(read(state.mountedOnLabel) == "Mounted on Gravel bike since 2026-07-01")
        #expect(read(state.retiredOnLabel) == "Retired 2026-09-01")
        #expect(state.isRetired)
        #expect(read(state.kindLabel) == "Chain")
        #expect(read(state.photoAction) == "Change photo")
        #expect(read(state.followersNote) != nil)
        #expect(state.photo is GearPhoto.Saved)
        #expect(read(EditGearTexts.shared.date(date: try #require(state.acquiredOn))) == "2026-06-20")
    }

    @Test func saveShowsOnlyOnceTheFormHasLoaded() throws {
        #expect(try #require(Samples.editGear["loaded"]).showsSave)
        #expect(!(try #require(Samples.editGear["loading"])).showsSave)
        #expect(!(try #require(Samples.editGearCollection["failed"])).showsSave)
    }

    @Test func savingDisablesTheForm() throws {
        #expect(!(try #require(Samples.editGearVariants["saving"])).enabled)
    }

    @Test func deletingAsksInTheSharedWords() throws {
        #expect(try #require(Samples.editGearVariants["confirmingDelete"]).confirmingDelete)
        #expect(String(localized: .gearDeleteTitle) == "Delete this gear?")
        #expect(
            String(localized: .gearDeleteMessage) == "Deleting removes it for good. Retiring keeps it and its details, out of the way.")
        #expect(String(localized: .gearRetire) == "Retire")
        #expect(String(localized: .gearDelete) == "Delete")
        #expect(String(localized: .gearUnretire) == "Put back in use")
    }

    @Test func theLimitsAreKotlins() {
        let limits = EditGearLimits.shared
        #expect(limits.weightDigits == 7)
        #expect(limits.starting == 9)
        #expect(limits.name == 100)
        #expect(limits.brand == 128)
        #expect(limits.notes == 1000)
    }

    @Test func theSportsSheetOffersBareSportsByCategory() throws {
        let groups = SportTypeGroups.shared.gearSports.elements(as: SportTypeGroup.self)
        let cycling = try #require(groups.first { read($0.label) == "Cycling" })
        let rows = cycling.rows.elements(as: SportTypeRow.self)
        #expect(rows.contains { $0.type.sport == "ride" && $0.type.subSport == nil })
        #expect(rows.allSatisfy { $0.type.subSport == nil && $0.nested.elements(as: KotlinActivityType.self).isEmpty })
    }

    @Test func theDefaultsSheetNestsSubSportsUnderTheirSport() throws {
        let groups = SportTypeGroups.shared.allTypes.elements(as: SportTypeGroup.self)
        let cycling = try #require(groups.first { read($0.label) == "Cycling" })
        let rows = cycling.rows.elements(as: SportTypeRow.self)
        let ride = try #require(rows.first { $0.type.sport == "ride" && $0.type.subSport == nil })
        let nested = ride.nested.elements(as: KotlinActivityType.self)
        #expect(nested.contains { $0.subSport == "gravel" })
        #expect(!rows.contains { $0.type.subSport == "gravel" })
        #expect(nested.map { read(LabelText.shared.activityType(type: $0)) }.contains("Gravel Ride"))
    }

    @Test func aCollectionsFormSaysWhatTheSharedOneSays() throws {
        let loaded = try #require(Samples.editGearCollection["loaded"])
        #expect(read(loaded.title) == "Edit collection")
        #expect(read(try #require(Samples.editGearCollection["new"]).title) == "New collection")
        #expect(loaded.members.elements(as: GearRow.self).map(\.name) == ["Tarmac", "Aero helmet", "Race shoes"])
        #expect(loaded.canAdd)
        #expect(String(localized: .gearCollectionsHelp) == "Gear you use together. Only you see your collections.")
        #expect(String(localized: .gearCollectionEmpty) == "No items yet")
        #expect(String(localized: .gearCollectionNameRequired) == "Give your collection a name")
        #expect(String(localized: .gearCollectionMoveUp("Aero helmet")) == "Move Aero helmet up")
        #expect(String(localized: .gearCollectionMoveDown("Aero helmet")) == "Move Aero helmet down")
        #expect(String(localized: .gearCollectionRemove("Aero helmet")) == "Remove Aero helmet")
    }

    @Test func aFullCollectionOffersNoAddItems() throws {
        #expect(!(try #require(Samples.editGearCollectionVariants["full"])).canAdd)
        #expect(String(localized: .gearCollectionFull) == "A collection holds at most 50 items.")
    }

    @Test func withNothingLeftAddItemsSaysSo() throws {
        #expect(try #require(Samples.editGearCollectionVariants["nothingToAdd"]).others.elements(as: GearRow.self).isEmpty)
        #expect(String(localized: .gearCollectionNoMoreItems) == "All your gear is in this collection.")
        #expect(!(try #require(Samples.editGearCollectionVariants["saving"])).enabled)
    }
}
