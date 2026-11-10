import SwiftUI
import Testing
import UIKit

@testable import ActivityTracker

/// The generated icons and images exist (dev/ios-native-resources): a symbol name the system doesn't have, or an image the asset
/// catalog lacks, draws nothing and says nothing.
@MainActor
struct AppIconTests {
    @Test func everyIconIsASymbolTheSystemHas() {
        let missing = AppIcon.allCases.flatMap { [$0.symbol, $0.filledSymbol] }.filter { UIImage(systemName: $0) == nil }
        #expect(missing.isEmpty, "No SF Symbol named \(missing)")
    }

    @Test func everySportImageIsInTheAssetCatalog() {
        let missing = SportIconNames.shared.all.filter { UIImage(named: "Drawable/\($0)") == nil }
        #expect(missing.isEmpty, "No image for \(missing)")
    }

    @Test func aSportsImageFollowsItsSubSport() {
        #expect(SportIconNames.shared.of(type: KotlinActivityType(sport: "ride", subSport: "eBike")) != SportIconNames.shared.of(type: KotlinActivityType(sport: "ride", subSport: nil)))
        #expect(SportIconNames.shared.of(type: KotlinActivityType(sport: "quidditch", subSport: nil)) == "sports_and_outdoors")
    }
}
