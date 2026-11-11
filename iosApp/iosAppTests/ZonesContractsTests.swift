import Testing

@testable import ActivityTracker

/// The Training zones, threshold history and Privacy zones contracts read from Swift (dev/ios-swift-interop), on the shared samples. The
/// pages are still the shared UI's; these hold that a native one can read every field it would draw, typed, without stopping the app.
@MainActor
struct ZonesContractsTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    private var defaults: ZoneSummary {
        get throws { try #require(Samples.trainingZones["loaded"]?.content.data as? TrainingZones).defaults }
    }

    @Test func theZoneKindsReachSwiftAsTheirOwnEnum() throws {
        let summary = try defaults
        #expect(summary.kinds.elements(as: ZoneKindOption.self).map(\.kind) == [.HeartRate, .Power])
        #expect(summary.offers(kind: .Power))
        #expect(!summary.offers(kind: .Speed))

        let running = try #require(Samples.sportZones["loaded"])
        #expect(running.kinds.elements(as: ZoneKindOption.self).map(\.kind) == [.HeartRate, .Speed])
        #expect(running.offers(kind: .Speed))
        #expect(!running.offers(kind: .Power))
    }

    @Test func computedZonesReachSwiftAsZones() throws {
        let zones = try #require(try defaults.heartRateZones).elements(as: Zone.self)
        #expect(zones.map(\.label) == ["1", "2", "3", "4", "5"])
        #expect(zones.map(\.name) == [.ActiveRecovery, .Endurance, .Tempo, .Threshold, .Vo2Max])
        #expect(zones.map(\.low) == [0, 117, 143, 162, 180])
        #expect(zones.map(\.high) == [116, 142, 161, 179, nil])
        #expect(zones.map { read(ZoneTexts.shared.label(zone: $0)) } == ["Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5"])

        let sport = try #require(Samples.sportZones["loaded"]?.heartRateZones).elements(as: Zone.self)
        #expect(sport.count == 5)
    }

    @Test func privacyZonesReachSwiftAsPrivacyZones() throws {
        let zones = try #require(Samples.privacyZones["loaded"]?.zones).elements(as: PrivacyZone.self)
        let home = try #require(zones.first)
        #expect(zones.count == 1)
        #expect(home.label == "Home")
        #expect(home.radiusMeters == 400)
        #expect(home.latitudeE7 == 400_150_000)
        #expect(home.longitudeE7 == -1_052_700_000)
        #expect(read(ZoneTexts.shared.privacyZoneName(zone: home, index: 0)) == "Home")
    }

    @Test func aCollapsedThresholdShowsItsNewestRows() throws {
        let section = try #require(try section(Samples.thresholdHistory["loaded"]))
        #expect(!section.showingAll)
        #expect(section.shownRows.map(\.current) == [true, false])
    }

    @Test func aThresholdShowingEveryRowReachesSwiftAsAnArray() throws {
        let section = try #require(try section(Samples.thresholdHistoryVariants["allRows"]))
        #expect(section.showingAll)
        #expect(section.shownRows.map(\.entry.value) == [250, 245, 240, 238, 230])
        #expect(section.shownRows.map(\.current) == [true, false, false, false, false])
    }

    private func section(_ state: ThresholdHistoryState?) throws -> ThresholdHistorySection? {
        let groups = try #require(state?.content.data as? [ThresholdHistoryGroup])
        return groups.first?.thresholds.elements(as: ThresholdHistorySection.self).first
    }
}
