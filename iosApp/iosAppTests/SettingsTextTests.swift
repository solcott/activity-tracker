import Testing

@testable import ActivityTracker

/// What Settings' list says and which row it marks (ui/settings-layout). Both are chosen in Kotlin (`SettingsTexts`, `SettingsPane`, with
/// their own tests); these hold that the catalog reads each choice as the shared list does.
@MainActor
struct SettingsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func rowsSummarizeTheirPages() {
        #expect(read(SettingsTexts.shared.defaultVisibility(visibility: "followers")) == "Followers")
        #expect(read(SettingsTexts.shared.privacyZones(count: 1, hidesRouteEnds: true)) == "1 zone, start and end hidden")
        #expect(read(SettingsTexts.shared.privacyZones(count: 2, hidesRouteEnds: false)) == "2 zones")
        #expect(read(SettingsTexts.shared.privacyZones(count: 0, hidesRouteEnds: false)) == "No zones")
        #expect(read(SettingsTexts.shared.segmentMatching(on: true)) == "Matching on")
        #expect(read(SettingsTexts.shared.segmentMatching(on: false)) == "Matching off")
    }

    @Test func aSummaryNotReadYetIsLeftOut() {
        #expect(read(SettingsTexts.shared.defaultVisibility(visibility: nil)) == nil)
        #expect(read(SettingsTexts.shared.privacyZones(count: nil, hidesRouteEnds: true)) == nil)
        #expect(read(SettingsTexts.shared.segmentMatching(on: nil)) == nil)
    }

    @Test func theUnitsNoteSaysWhatTheChoiceMeasuresInOrThatItIsSaving() {
        #expect(read(SettingsTexts.shared.unitsNote(units: .Metric, saving: false)) == "Kilometers, meters, °C")
        #expect(read(SettingsTexts.shared.unitsNote(units: .Imperial, saving: false)) == "Miles, feet, °F")
        #expect(read(SettingsTexts.shared.unitsNote(units: .Imperial, saving: true)) == "Saving…")
    }

    @Test func theListMarksOnlyTheRowOfThePageBesideIt() {
        let layout = SettingsLayout.beside(SettingsRows.privacyZones)

        #expect(layout.marks(SettingsRows.privacyZones))
        #expect(!layout.marks(SettingsRows.activityDefaults))
        #expect(!layout.marks(SettingsRows.gear))
    }

    @Test func theBlockedAndMutedListsAreToldApart() {
        let layout = SettingsLayout.beside(SettingsRows.muted)

        #expect(layout.marks(SettingsRows.muted))
        #expect(!layout.marks(SettingsRows.blocked))
    }

    @Test func noRowIsMarkedOnePageAtATime() {
        #expect(!SettingsLayout.single.marks(SettingsRows.activityDefaults))
    }
}
