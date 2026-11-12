import Testing

@testable import ActivityTracker

/// What the Search tab and the choice after leaving on another device say, read from Swift over every sample (segments/segment-browse,
/// auth/leaving, dev/shared-display-text, dev/ios-swift-interop).
@MainActor
struct SearchTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func everySearchSampleReads() {
        for state in Samples.athleteSearch.values.map({ $0 }) + Samples.athleteSearchVariants.values.map({ $0 }) {
            _ = read(AthleteSearchTexts.shared.title(mode: state.mode))
            _ = read(AthleteSearchTexts.shared.prompt(state: state))
            _ = read(AthleteSearchTexts.shared.empty(state: state))
            _ = state.results.items.compactMap { $0 as? ProfileViewBasic }.map { AppPackage.getShownName($0) }
        }
    }

    @Test func theModesAndTheirTitles() {
        #expect(SearchMode.allCases.map { read(AthleteSearchTexts.shared.mode(mode: $0)) } == ["Athletes", "Segments"])
        #expect(read(AthleteSearchTexts.shared.title(mode: .Athletes)) == "Find athletes")
        #expect(read(AthleteSearchTexts.shared.title(mode: .Segments)) == "Search")
        #expect(Samples.athleteSearchVariants["segments"]!.mode == .Segments)
    }

    @Test func aBlankQueryPromptsAndNoMatchSaysSo() {
        let blank = Samples.athleteSearchVariants["blank"]!
        #expect(read(AthleteSearchTexts.shared.prompt(state: blank)) == "Find athletes who use this app by name, @handle or DID.")
        #expect(read(AthleteSearchTexts.shared.empty(state: blank)) == nil)
        let none = Samples.athleteSearchVariants["noMatch"]!
        #expect(read(AthleteSearchTexts.shared.prompt(state: none)) == nil)
        #expect(read(AthleteSearchTexts.shared.empty(state: none)) == "No athletes match “zzz”.")
    }

    @Test func everyLeftElsewhereSampleReads() {
        for state in Samples.leftElsewhere.values {
            #expect(read(LeftElsewhereTexts.shared.intro(state: state))?.contains("bob.test") == true)
        }
        let everything = Samples.leftElsewhere["everything"]!
        #expect(
            LeftElsewhereTexts.shared.lines(state: everything).map { read($0) } == [
                "12 changes are pending.", "An import is still saving.", "A data export hasn't been saved.",
                "A recording is in progress.",
            ])
        #expect(LeftElsewhereTexts.shared.lines(state: Samples.leftElsewhere["loaded"]!).map { read($0) } == ["1 change is pending."])
    }
}
