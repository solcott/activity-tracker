import Testing

@testable import ActivityTracker

/// What the native filter bar says and marks, read from Swift on the shared samples (dev/shared-display-text, dev/ios-swift-interop):
/// the same words the shared bar resolves, and the picker's marks from `TypePickerState.selectionOf`.
@MainActor
struct FeedFilterTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    @Test func theChipsNameTheFilter() throws {
        let loaded = try #require(Samples.feedFilter["loaded"]).filter
        #expect(read(FeedFilterTexts.shared.types(filter: loaded)) == "All types")
        #expect(read(FeedFilterTexts.shared.range(range: loaded.range)) == "Any time")

        let narrowed = try #require(Samples.feedFilter["narrowed"]).filter
        #expect(read(FeedFilterTexts.shared.types(filter: narrowed)) == "Ride")
        #expect(read(FeedFilterTexts.shared.range(range: narrowed.range)) == "Last 30 days")

        let custom = try #require(Samples.feedFilter["custom"]).filter
        #expect(read(FeedFilterTexts.shared.types(filter: custom)) == "Ride + 2 more")
        #expect(read(FeedFilterTexts.shared.range(range: custom.range)) == "2026-03-01 – 2026-03-15")
    }

    @Test func theDateMenuOffersAnyTimeThePresetsThenCustom() throws {
        let words = FeedFilterTexts.shared.rangeChoices().map { read(FeedFilterTexts.shared.range(range: $0.range)) }
        #expect(words == ["Any time", "Last 7 days", "Last 30 days", "This year"])
        let narrowed = try #require(Samples.feedFilter["narrowed"]).filter
        #expect(FeedFilterTexts.shared.chosenRange(range: narrowed.range) == 2)
        let custom = try #require(Samples.feedFilter["custom"]).filter
        #expect(FeedFilterTexts.shared.chosenRange(range: custom.range) == 4)
    }

    @Test func thePickerMarksEachTypeAndCategory() throws {
        let picker = try #require(Samples.feedFilter["picking"]?.typePicker)
        let running = try #require(picker.categories.elements(as: CategorySelection.self).first)
        #expect(running.category == .Running)
        #expect(running.selection == .Partial)
        let marks = running.types.elements(as: KotlinActivityType.self).map { picker.selectionOf(type: $0) }
        #expect(marks == [.Partial, .All, .None])
        #expect(read(LabelText.shared.sportCategory(category: running.category)) == "Running")
    }
}
