import Foundation
import Testing

@testable import ActivityTracker

/// What the create-segment form says and offers (dev/shared-display-text). The rules are Kotlin's, with their own tests; these hold that
/// Swift reads each on the shared samples, and the catalog words each as the shared form does.
@MainActor
struct CreateSegmentTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func aStretchReadsItsLengthAndSportGroupOrWhyItCantBeASegment() throws {
        let texts = CreateSegmentTexts.shared
        let loaded = try #require(Samples.createSegment["loaded"])
        #expect(read(texts.selection(state: loaded, units: .Metric))?.hasSuffix(" · Ride") == true)
        #expect(!texts.selectionFails(state: loaded))
        let short = try #require(Samples.createSegment["invalid"])
        #expect(read(texts.selection(state: short, units: .Metric)) == "Ride segments must be at least 0.20 km")
        #expect(texts.selectionFails(state: short))
        let gap = try #require(Samples.createSegmentVariants["crossesGap"])
        #expect(read(texts.selection(state: gap, units: .Metric))?.contains("privacy zone") == true)
        #expect(read(texts.along(meters: 480, units: .Metric)) == "0.48 km along the route")
    }

    @Test func theFormSaysWhyNoSegmentCanBeMadeAndWhatWillBePublic() throws {
        let texts = CreateSegmentTexts.shared
        let hidden = try #require(Samples.createSegmentVariants["allHidden"])
        let reason = try #require(hidden.unavailable)
        #expect(read(texts.unavailable(reason: reason))?.contains("privacy zones") == true)
        let alone = try #require(read(texts.publicNote(namesActivity: false)))
        #expect(read(texts.publicNote(namesActivity: true))?.hasPrefix(alone + " ") == true)
        let duplicate = try #require(Samples.createSegmentVariants["duplicate"])
        let segment = try #require(duplicate.duplicates.elements(as: SegmentView.self).first)
        #expect(read(texts.duplicate(segment: segment, units: .Metric))?.hasPrefix("Ridge Trail climb · ") == true)
    }

    @Test func publishWaitsForANamedValidStretchAndTheMapHighlightsIt() throws {
        let loaded = try #require(Samples.createSegment["loaded"])
        #expect(loaded.canPublish)
        let short = try #require(Samples.createSegment["invalid"])
        let unnamed = try #require(Samples.createSegmentVariants["unnamed"])
        #expect(!short.canPublish)
        #expect(!unnamed.canPublish)
        let map = try #require(loaded.routeMap)
        #expect(map.highlight != nil)
    }
}
