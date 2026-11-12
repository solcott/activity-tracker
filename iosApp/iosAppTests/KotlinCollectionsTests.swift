import Testing

@testable import ActivityTracker

/// A contract's immutable list read from Swift as a typed array (dev/ios-swift-interop), on the settings pages' sample states: Settings'
/// own state has no collection.
@MainActor
struct KotlinCollectionsTests {
    @Test func aListOfTextIsSwiftStrings() throws {
        let zones = try #require(Samples.trainingZones["loaded"]?.content.data as? SettingsPackage.TrainingZones)

        #expect(zones.addableSports.elements(as: String.self) == ["run", "ride"])
    }

    @Test func aListOfWholeNumbersIsSwiftIntegers() throws {
        let state = try #require(Samples.privacyZones["loaded"])

        // A Kotlin Int is a 32-bit integer in Swift, never Swift's own `Int`.
        #expect(state.choices.elements(as: Int32.self) == [200, 400, 600, 800])
    }

    @Test func aListOfAContractsObjectsIsThoseObjects() throws {
        // Through the state's typed getter, never a cast of the erased `content.data` to a Swift array: that bridges the Kotlin list
        // through `NSArray`, which once threw (`count` unrecognized) under a full run's load.
        let groups = try #require(Samples.thresholdHistory["loaded"]?.groups).elements(as: SettingsPackage.ThresholdHistoryGroup.self)
        let group = try #require(groups.first)

        let thresholds = group.thresholds.elements(as: SettingsPackage.ThresholdHistorySection.self)
        #expect(thresholds.count == 1)
        let rows = try #require(thresholds.first).rows.elements(as: SettingsPackage.ThresholdHistoryRow.self)
        #expect(rows.map(\.current) == [true, false])
    }

    @Test func anEmptyListIsAnEmptyArray() throws {
        let zones = try #require(Samples.trainingZones["loaded"]?.content.data as? SettingsPackage.TrainingZones)

        #expect(zones.sports.elements(as: SettingsPackage.ZoneSummary.self).isEmpty)
    }

    @Test func aListNotReadYetIsNil() throws {
        let state = try #require(Samples.privacyZones["loading"])

        #expect(state.zones == nil)
    }
}
