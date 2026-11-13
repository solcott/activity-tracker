import Foundation
import Testing

@testable import ActivityTracker

/// What the gear item page says and offers (gear/gear-usage, gear/gear-components, dev/shared-display-text, dev/ios-swift-interop). The
/// rules are Kotlin's, with their own tests; these hold that Swift reads each on the shared samples, typed, and the catalog words each as
/// the shared page does.
@MainActor
struct GearItemTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private func page(_ sample: String) throws -> GearPage {
        try #require((Samples.gear[sample] ?? Samples.gearVariants[sample])?.page)
    }

    private let utc = KotlinTimeZone.Companion.shared.of(zoneId: "UTC")

    @Test func theOwnersBikeReadsItsDetailsAndTotals() throws {
        let state = try #require(Samples.gear["loaded"])
        let bike = try page("loaded")
        #expect(state.title == "Gravel bike")
        #expect(state.showsEdit)
        #expect(state.showsCollections)
        #expect(!state.authorClickable)
        #expect(read(GearPageTexts.shared.kindLine(gear: bike.gear)) == "Gravel")
        #expect(read(GearPageTexts.shared.maker(gear: bike.gear)) == "Rivendell Appaloosa")
        #expect(read(GearPageTexts.shared.notes(page: bike)) == "Public")
        let figures = GearPageTexts.shared.figures(page: bike, units: .Metric)
        #expect(figures.map { read($0.label) } == ["Activities", "Moving time", "Distance", "Elevation gain", "Vertical", "Days used"])
        #expect(read(figures[2].value) == "2,065 km")
        #expect(read(GearPageTexts.shared.starting(starting: bike.usage.starting, units: .Metric)) == "Includes 2,000 km before tracking")
        #expect(GearPageTexts.shared.bySport(page: bike, units: .Metric).map { read($0.value) } == ["1 activity · 32 km", "1 activity · 32 km"])
        #expect(GearPageTexts.shared.byYear(page: bike, units: .Metric).map { read($0.label) } == ["2026", "2025"])
    }

    @Test func componentsReadTheirLinesAndDates() throws {
        let bike = try page("components")
        let now = try #require(bike.components.elements(as: GearComponent.self).first)
        let before = try #require(bike.previous.elements(as: GearComponent.self).first)
        #expect(read(GearPageTexts.shared.componentLine(component: now, units: .Metric)) == "Chain · 32 km")
        #expect(read(GearPageTexts.shared.componentDates(component: now, zone: utc)) == "Since Jul 1, 2026")
        #expect(read(GearPageTexts.shared.componentDates(component: before, zone: utc)) == "Mar 1, 2025 – Jul 1, 2026")
        #expect(read(GearPageTexts.shared.componentActions(component: now)) == "Actions for Chain #4")
        let chain = try page("chain")
        let mount = try #require(chain.mounts.elements(as: GearMountView.self).first)
        #expect(mount.parent.name == "Gravel bike")
        #expect(read(GearPageTexts.shared.mountDates(mount: mount, zone: utc)) == "Mar 1, 2025 – Jul 1, 2026")
    }

    @Test func onlyTheOwnerManagesComponents() throws {
        let owner = try #require(Samples.gearVariants["components"])
        #expect(owner.managesComponents && owner.showsComponents && owner.mountEnabled)
        let follower = try #require(Samples.gearVariants["componentsFollower"])
        #expect(!follower.managesComponents && follower.showsComponents && !follower.showsEdit && follower.authorClickable)
        #expect(!(try #require(Samples.gearVariants["follower"])).showsComponents)
        #expect(!(try #require(Samples.gearVariants["mountSaving"])).mountEnabled)
    }

    @Test func aFailedMountShowsByComponents() throws {
        let state = try #require(Samples.gearVariants["mountFailed"])
        #expect(state.componentsError?.kind == .MountLoop)
        #expect(state.activitiesError == nil)
    }

    @Test func thePickerIsTitledByWhatItReplaces() throws {
        let replacing = try #require(Samples.gearVariants["replacing"]?.picker)
        #expect(read(GearPageTexts.shared.pickerTitle(picker: replacing)) == "Replace Chain #4")
        let candidates = replacing.candidates.elements(as: GearMountCandidate.self)
        #expect(candidates.map { read(GearPageTexts.shared.candidateLine(candidate: $0)) } == ["Chain", "Power meter"])
        let none = try #require(Samples.gearVariants["mountingNone"]?.picker)
        #expect(read(GearPageTexts.shared.pickerTitle(picker: none)) == "Mount on this item")
        #expect(none.candidates.elements(as: GearMountCandidate.self).isEmpty)
    }

    @Test func theUnmountDateStartsTheDayAfterTheMount() throws {
        let state = try #require(Samples.gearVariants["unmounting"])
        let first = try #require(state.unmountFirstDay(zone: utc))
        #expect(CalendarDay(first) == CalendarDay(year: 2026, month: 7, day: 2))
        let initial = try #require(state.unmountInitialDay(now: Samples.clock.now, zone: utc))
        #expect(CalendarDay(initial).date(in: .current) >= CalendarDay(first).date(in: .current))
        #expect(Samples.gear["loaded"]?.unmountFirstDay(zone: utc) == nil)
    }

    @Test func linkedDevicesAndProfilesRead() throws {
        let state = try #require(Samples.gearVariants["linked"])
        let sensors = state.linkedSensors.elements(as: LinkedSensor.self)
        #expect(sensors.map { read(LinkedSensorTexts.shared.unlink(sensor: $0)) } == ["Unlink SRAM 1052", "Unlink Garmin HRM 200"])
        #expect(state.linkedProfiles.elements(as: String.self) == ["ROAD"])
    }

    @Test func notFoundSaysSo() throws {
        let state = try #require(Samples.gearVariants["notFound"])
        #expect(state.notFound && state.page == nil && !state.showsEdit)
        #expect(String(localized: .gearNotFound).hasPrefix("This gear isn't available."))
    }
}
