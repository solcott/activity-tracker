import SwiftUI
import Testing

@testable import ActivityTracker

/// The shared strings as the native UI reads them (dev/ios-native-resources, dev/shared-display-text): by generated symbol, and by
/// name for text chosen in Kotlin.
@MainActor
struct AppStringsTests {
    private let source = CatalogText.shared

    @Test func aSymbolReadsAsTheSharedString() {
        #expect(String(localized: .loginSignIn) == "Sign in")
        #expect(String(localized: .errorWithReason("Couldn't load your feed", "Couldn't reach the server."))
            == "Couldn't load your feed. Couldn't reach the server.")
    }

    @Test func aPercentSignIsWrittenOnce() {
        #expect(String(localized: .zoneModelPercentMaxHeartRate) == "% of max heart rate (5)")
        #expect(String(localized: .sensorBatteryPercent("80")) == "Battery 80%")
        #expect(source.string(name: "zone_model_percentMaxHeartRate", args: []) == "% of max heart rate (5)")
        #expect(source.string(name: "sensor_battery_percent", args: ["80"]) == "Battery 80%")
    }

    @Test func anItemOfAStringArrayIsNamedByItsIndex() {
        #expect(String(localized: .segmentRankOrdinals2) == "3rd")
    }

    @Test func aPluralChoosesItsFormByQuantity() {
        #expect(String(localized: .likeCount(quantity: 1, "1")) == "1 like")
        #expect(String(localized: .likeCount(quantity: 3, "3")) == "3 likes")
        #expect(String(localized: .cardWithOthers(quantity: 1, "bob", "1")) == "with bob and 1 other")
        #expect(source.plural(name: "like_count", quantity: 1, args: ["1"]) == "1 like")
        #expect(source.plural(name: "like_count", quantity: 0, args: ["0"]) == "0 likes")
        #expect(source.plural(name: "card_with_others", quantity: 2, args: ["bob", "2"]) == "with bob and 2 others")
    }

    @Test func aNameTheCatalogLacksIsNoString() {
        #expect(source.string(name: "no_such_string", args: []) == nil)
        #expect(source.plural(name: "no_such_plural", quantity: 1, args: []) == nil)
    }

    @Test func textChosenInKotlinReadsAsTheSharedUiReadsIt() {
        let imperial = Stats(units: .Imperial)
        let metric = Stats(units: .Metric)
        #expect(imperial.distance(4_828) == "3.00 mi")
        #expect(metric.distance(4_828) == "4.83 km")
        #expect(metric.pace(meters: nil, seconds: 600) == "–")
        #expect(imperial.elevation(597.2) == "1,959 ft")
        #expect(Stats.duration(seconds: 36_303) == "10:05:03")
        #expect(metric.rate(sport: "run", meters: 5_000, seconds: 1_500)?.value == "5:00 /km")
        #expect(metric.rate(sport: "yoga", meters: nil, seconds: 3_600) == nil)
    }

    @Test func aLabelFallsBackWhereTheAppHasNoString() {
        #expect(Labels.sport("run") == "Run")
        #expect(Labels.sport("quidditch") == "Quidditch")
        #expect(Labels.activityType(KotlinActivityType(sport: "ride", subSport: "mountain")) == "Mountain Bike Ride")
        #expect(Labels.activityType(KotlinActivityType(sport: "ride", subSport: "unicycle")) == "Ride")
        #expect(Labels.sensorType("tricorder") == Labels.sensorType("other"))
        #expect(Labels.handle("alice.test") == "@alice.test")
    }

    @Test func anErrorSaysWhatFailedAndWhyWhenThatHelps() {
        let offline = UiError(kind: .LoadFeed, reason: DataError.Network.shared)
        #expect(AppStrings.string(ErrorTexts.shared.of(error: offline)) == "Couldn't load your feed. Couldn't reach the server.")
        #expect(AppStrings.string(ErrorTexts.shared.of(error: UiError(kind: .LoadFeed, reason: nil))) == "Couldn't load your feed")
    }
}
