import Testing

@testable import ActivityTracker

/// What the native zones pages say, limit and enable, read through Kotlin's choices on the shared samples (ui/settings-layout,
/// dev/shared-display-text): the same words the Compose pages show.
@MainActor
struct ZonesPagesTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    private func zones(_ state: TrainingZonesState?) throws -> TrainingZones {
        try #require(state?.content.data as? TrainingZones)
    }

    @Test func theDefaultsListTheirThresholds() throws {
        let defaults = try zones(Samples.trainingZones["loaded"]).defaults
        let lines = TrainingZonesTexts.shared.thresholds(summary: defaults)
        #expect(lines.map { read($0.label) } == ["Threshold heart rate", "Max heart rate", "Resting heart rate", "Threshold power"])
        #expect(lines.map { read($0.value) } == ["170 bpm", "190 bpm", "55 bpm", "250 W"])
        #expect(lines.allSatisfy { !$0.muted })
        #expect(read(TrainingZonesTexts.shared.heading(summary: defaults)) == "Default zones")
    }

    @Test func aSportMarksWhatComesFromTheDefaults() throws {
        let swim = try #require(try zones(Samples.trainingZonesVariants["sports"]).sports.elements(as: ZoneSummary.self).first)
        let lines = TrainingZonesTexts.shared.thresholds(summary: swim)
        #expect(lines.map { read($0.value) } == ["1:45 /100 m", "165 bpm", "190 bpm (default)"])
        #expect(lines.map(\.muted) == [false, false, true])
        #expect(read(TrainingZonesTexts.shared.heading(summary: swim)) == "Swim zones")
        #expect(swim.offers(kind: .Speed) && !swim.offers(kind: .Power))

        let run = try #require(try zones(Samples.trainingZonesVariants["runDefaults"]).sports.elements(as: ZoneSummary.self).first)
        let runLines = TrainingZonesTexts.shared.thresholds(summary: run)
        #expect(runLines.map { read($0.value) } == ["Not set", "170 bpm (default)", "190 bpm (default)", "250 W (default)"])
        #expect(runLines.allSatisfy { $0.muted })
    }

    @Test func zonesReadWithTheirUnits() throws {
        let defaults = try zones(Samples.trainingZones["loaded"]).defaults
        let heartRate = try #require(defaults.heartRateZones).elements(as: Zone.self)
        #expect(read(ZoneTexts.shared.range(zone: heartRate[2], kind: .HeartRate, paceUnit: .PerKilometer)) == "143–161 bpm")
        #expect(read(ZoneTexts.shared.range(zone: heartRate[4], kind: .HeartRate, paceUnit: .PerKilometer)) == "180+ bpm")
        #expect(read(ZoneTexts.shared.name(zone: heartRate[1])) == "Endurance")
        #expect(read(ZoneTexts.shared.model(model: defaults.heartRateModel)) == "Andy Coggan (5)")
        #expect(read(ZoneTexts.shared.basis(basis: defaults.heartRateModel.basis)) == "Lactate threshold")
        let power = try #require(defaults.powerZones).elements(as: Zone.self)
        #expect(read(ZoneTexts.shared.range(zone: power[0], kind: .Power, paceUnit: .PerKilometer)).hasSuffix(" W"))

        let swim = try #require(try zones(Samples.trainingZonesVariants["sports"]).sports.elements(as: ZoneSummary.self).first)
        let paces = try #require(swim.speedZones).elements(as: Zone.self).map {
            read(ZoneTexts.shared.range(zone: $0, kind: .Speed, paceUnit: swim.paceUnit))
        }
        #expect(try #require(paces.first).hasPrefix("Slower than "))
        #expect(try #require(paces.last).hasSuffix("/100 m or faster"))
        #expect(read(ZoneTexts.shared.missing(basis: .FunctionalThresholdPower)) == "Enter your threshold power to see these zones.")
    }

    @Test func theDefaultsFormLimitsAndSaves() throws {
        let state = try #require(Samples.zones["loaded"])
        #expect(state.canSave)
        let rules = ThresholdFields.rules(.MaxHeartRate)
        #expect(rules == TextFieldRules(digitsOnly: true, maxLength: 3))
        #expect(ThresholdFields.rules(.Power).maxLength == 4)
        #expect(!ThresholdFields.rules(.Pace).digitsOnly)

        // A field driven as the view drives it: Save waits for a value in range.
        let max = TextFieldModel(state.maxHeartRate, rules: rules)
        max.edit("3")
        max.edit("30")
        max.edit("300")
        #expect(!state.isValid(threshold: .MaxHeartRate, text: max.text))
        #expect(!state.canSave)
        max.edit("1a8")
        #expect(max.text == "18")
        max.edit("185")
        #expect(state.canSave)

        #expect(!(try #require(Samples.zones["invalid"])).canSave)
        #expect(!(try #require(Samples.zonesVariants["saving"])).canSave)
        #expect(try #require(Samples.zonesVariants["autoUpdate"]).autoUpdates(threshold: .MaxHeartRate))
        #expect(!state.autoUpdates(threshold: .MaxHeartRate))
    }

    @Test func aSportsFormSaysItsSportAndDefaults() throws {
        let state = try #require(Samples.sportZones["loaded"])
        #expect(read(SportZonesTexts.shared.title(sport: state.sport)) == "Run zones")
        #expect(read(SportZonesTexts.shared.paceLabel(unit: state.paceUnit)) == "Threshold pace (m:ss /km)")
        #expect(read(SportZonesTexts.shared.remove(sport: state.sport)) == "Remove Run zones")
        #expect(SportZonesTexts.shared.defaultHint(state: state, threshold: .MaxHeartRate).map(read) == "Default: 190")
        #expect(SportZonesTexts.shared.defaultHint(state: state, threshold: .Pace) == nil)
        #expect(state.isValid(threshold: .Pace, text: "4:30"))
        #expect(!(try #require(Samples.sportZones["invalid"])).canSave)

        let swim = try #require(Samples.sportZonesVariants["swim"])
        #expect(read(SportZonesTexts.shared.paceLabel(unit: swim.paceUnit)) == "Threshold pace (m:ss /100 m)")
        #expect(try #require(Samples.sportZonesVariants["saved"]).saved)
    }

    @Test func privacyZonesAreNamedAndMeasured() throws {
        let phone = try #require(Samples.privacyZonesVariants["phone"])
        let zones = try #require(phone.zones).elements(as: PrivacyZone.self)
        #expect(zones.enumerated().map { read(ZoneTexts.shared.privacyZoneName(zone: $1, index: Int32($0))) } == ["Home", "Zone 2"])
        #expect(phone.hideEndsChoices.elements(as: Int32.self).first == 0)
        #expect(read(PrivacyZonesTexts.shared.distance(meters: 0, units: .Metric)) == "Off")
        #expect(read(PrivacyZonesTexts.shared.distance(meters: 400, units: .Metric)) == "0.40 km")
        #expect(read(PrivacyZonesTexts.shared.addHere(locating: false)) == "Add a zone at my location")
        #expect(read(PrivacyZonesTexts.shared.addHere(locating: true)) == "Finding your location…")
        #expect(phone.enabled && phone.canAddHere)
        #expect(!(try #require(Samples.privacyZonesVariants["locating"])).canAddHere)
        #expect(!(try #require(Samples.privacyZonesVariants["signInAgain"])).enabled)
    }

    @Test func zonesCanBeShownToEveryVisibility() throws {
        let state = try #require(Samples.trainingZones["loaded"])
        #expect(state.visibilityOptions.elements(as: String.self).count == 4)
    }
}
