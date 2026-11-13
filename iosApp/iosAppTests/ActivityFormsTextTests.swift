import Foundation
import Testing

@testable import ActivityTracker

/// What the activity forms and the dive site form say and offer (dev/shared-display-text). The rules are Kotlin's, with their own tests;
/// these hold that Swift reads each on the shared samples, and the catalog words each as the shared forms do.
@MainActor
struct ActivityFormsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func theGearFieldNamesTheChosenItemsAndEachItemsLine() throws {
        let field = try #require(Samples.manualActivityVariants["full"]).gear
        #expect(read(GearFieldTexts.shared.summary(field: field)) == "Tarmac")
        let options = field.options.elements(as: GearOption.self)
        #expect(options.map { field.isSelected(option: $0) } == [true, false, false])
        #expect(options.map { read(GearFieldTexts.shared.line(option: $0)) } == ["Road · Public", "Shoes · Followers", "Road · Only me · Retired"])
        let collection = try #require(field.collections.elements(as: GearCollectionOption.self).first)
        #expect(read(GearFieldTexts.shared.collection(collection: collection)) == "Race day · 3 items")
        #expect(GearFieldTexts.shared.notAdded(field: field) == nil)
        #expect(field.offersClear)
        let empty = try #require(Samples.manualActivity["loaded"]).gear
        #expect(read(GearFieldTexts.shared.summary(field: empty)) == "No gear")
    }

    @Test func manualEntryLabelsItsUnitsAndSavesOnceADurationIsEntered() throws {
        #expect(read(ManualActivityTexts.shared.distanceLabel(units: .Metric)) == "Distance (km)")
        #expect(read(ManualActivityTexts.shared.temperatureLabel(units: .Imperial)) == "Temperature (°F)")
        let loaded = try #require(Samples.manualActivity["loaded"])
        let invalid = try #require(Samples.manualActivity["invalid"])
        #expect(loaded.valid)
        #expect(!invalid.valid)
    }

    @Test func theTypePickerOffersTheRidesSubSportsAndMatchesASearch() throws {
        let field = try #require(Samples.manualActivityVariants["full"]).type
        let choices = SportTypeGroups.shared.subSportChoices(selected: field.selected, outdoorOnly: false)
            .elements(as: KotlinActivityType.self)
        let first = try #require(choices.first)
        #expect(read(LabelText.shared.activityType(type: first)) == "Ride")
        #expect(choices.contains { $0.key == field.selected.key })
        let gravel = SportTypeGroups.shared.matching(query: "gravel", source: CatalogText.shared, outdoorOnly: false)
            .elements(as: SportTypeGroup.self)
        #expect(gravel.flatMap { $0.types.elements(as: KotlinActivityType.self) }.map(\.key) == ["ride/gravel"])
        let yoga = try #require(Samples.manualActivityVariants["noSubSports"]).type.selected
        #expect(SportTypeGroups.shared.subSportChoices(selected: yoga, outdoorOnly: false).size == 0)
    }

    @Test func theEditFormCountsItsPhotosAndNamesTheWidenedCharts() throws {
        let state = try #require(Samples.editActivityVariants["full"])
        let details = try #require(state.details)
        let dive = try #require(Samples.editActivityVariants["dive"])
        #expect(read(DetailsFormTexts.shared.photosCount(fields: details)) == "2 of 4 photos")
        #expect(details.canAddMedia)
        #expect(dive.details?.canAddMedia == false)
        let body = try #require(read(EditActivityTexts.shared.coreChartsBody(visibility: "followers")))
        #expect(body.hasSuffix("Followers."))
    }

    @Test func theDiveSiteFormSaysItsRadiusAndWhyNoSiteCanBeMade() throws {
        #expect(read(CreateDiveSiteTexts.shared.radius(meters: 100, units: .Metric)) == "Radius: 100 m")
        #expect(read(CreateDiveSiteTexts.shared.radius(meters: 100, units: .Imperial)) == "Radius: 328 ft")
        let inside = try #require(Samples.createDiveSite["insideZone"])
        let reason = try #require(inside.unavailable)
        #expect(read(CreateDiveSiteTexts.shared.unavailable(reason: reason))?.contains("privacy zones") == true)
        let alone = try #require(read(CreateDiveSiteTexts.shared.publicNote(namesDive: false)))
        #expect(read(CreateDiveSiteTexts.shared.publicNote(namesDive: true))?.hasPrefix(alone + " ") == true)
        let site = try #require(Samples.createDiveSite["loaded"])
        let unnamed = try #require(Samples.createDiveSite["invalid"])
        #expect(site.canPublish)
        #expect(!unnamed.canPublish)
        #expect(site.routeMap != nil)
    }
}
