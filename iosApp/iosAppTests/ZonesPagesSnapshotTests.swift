import SwiftUI
import Testing

@testable import ActivityTracker

/// Training zones, the default and sport zones forms and Privacy zones in each of their sample states, against their references
/// (ui/settings-layout, dev/ios-snapshot-tests), each recorded tall enough for the whole page; and a sport's zones beside Settings'
/// list as an iPad lays it out, the column's second page with its back button. The menus present over the page, so their words are
/// `ZonesPagesTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ZonesPagesSnapshotTests {
    nonisolated private static let trainingSamples = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let trainingVariants = ["sports", "runDefaults", "noZones", "saveFailed"]
    nonisolated private static let zonesSamples = ["loading", "failed", "loaded", "invalid"]
    nonisolated private static let zonesVariants = ["saving", "saveFailed", "autoUpdate", "missing"]
    nonisolated private static let sportSamples = ["loading", "failed", "loaded", "invalid"]
    nonisolated private static let sportVariants = ["saved", "swim", "saving", "saveFailed"]
    nonisolated private static let privacySamples = ["loading", "failed", "loaded"]
    nonisolated private static let privacyVariants = ["phone", "locating", "empty", "signInAgain", "saveFailed"]

    /// As tall as an image may be at three pixels a point (8,192 pixels), which the longest sample needs.
    private static let trainingSize = CGSize(width: 393, height: 2_730)
    private static let formSize = CGSize(width: 393, height: 1_900)
    private static let privacySize = CGSize(width: 393, height: 1_000)

    @Test(arguments: trainingSamples + trainingVariants)
    func trainingZones(sample: String) throws {
        let state = try #require(Samples.trainingZones[sample] ?? Samples.trainingZonesVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { TrainingZonesView(state: state) }, size: Self.trainingSize, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: zonesSamples + zonesVariants)
    func defaultZones(sample: String) throws {
        let state = try #require(Samples.zones[sample] ?? Samples.zonesVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { ZonesView(state: state) }, size: Self.formSize, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: sportSamples + sportVariants)
    func sportZones(sample: String) throws {
        let state = try #require(Samples.sportZones[sample] ?? Samples.sportZonesVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { SportZonesView(state: state) }, size: Self.formSize, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: privacySamples + privacyVariants)
    func privacyZones(sample: String) throws {
        let state = try #require(Samples.privacyZones[sample] ?? Samples.privacyZonesVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { PrivacyZonesView(state: state) }, size: Self.privacySize, named: sample, precision: Snapshots.barButtons)
    }

    /// Run's zones beside the list, as the column's second page: a back button in its header, Save at its trailing end.
    @Test func sportZonesBesideTheList() throws {
        let state = try #require(Samples.sportZonesVariants["saved"])
        let page = SportZonesView(state: state).environment(\.inPane, true).environment(\.paneRoot, false)
        assertAppSnapshot(
            of: NavigationStack {
                SettingsColumns(
                    list: {
                        SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(SettingsRows.trainingZones))
                    },
                    page: page)
            },
            size: CGSize(width: 1_024, height: 1_366), named: "sportZonesBeside", precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.trainingZones.keys) == Set(Self.trainingSamples))
        #expect(Set(Samples.trainingZonesVariants.keys) == Set(Self.trainingVariants))
        #expect(Set(Samples.zones.keys) == Set(Self.zonesSamples))
        #expect(Set(Samples.zonesVariants.keys) == Set(Self.zonesVariants))
        #expect(Set(Samples.sportZones.keys) == Set(Self.sportSamples))
        #expect(Set(Samples.sportZonesVariants.keys) == Set(Self.sportVariants))
        #expect(Set(Samples.privacyZones.keys) == Set(Self.privacySamples))
        #expect(Set(Samples.privacyZonesVariants.keys) == Set(Self.privacyVariants))
    }
}
