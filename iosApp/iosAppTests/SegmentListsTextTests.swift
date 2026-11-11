import Testing

@testable import ActivityTracker

/// What Starred segments and the three boards say and offer natively, as Kotlin chooses it for both UIs (segments/segment-favorites,
/// segments/segment-leaderboards, segments/segment-mayors, dive-sites/site-regulars, dev/shared-display-text), on the shared samples.
@MainActor
struct SegmentListsTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    private func labels(_ choice: BoardChoice) -> [String] { choice.options.elements(as: BoardOption.self).map { read($0.label) } }

    @Test func aStarReadsItsLengthAndTypeAndOpensWhileItsSegmentIsThere() throws {
        let state = try #require(Samples.starredSegmentsVariants["unavailable"])
        let stars = state.segments.items.compactMap { $0 as? StarredSegment }
        #expect(stars.count == 3)
        let ridge = try #require(stars[0].segment)
        #expect(ridge.currentName == "Ridge Trail")
        #expect(read(StarredSegmentsTexts.shared.summary(segment: ridge, units: .Metric)) == "1.00 km · Ride")
        let lift = try #require(stars[2].segment)
        #expect(read(StarredSegmentsTexts.shared.summary(segment: lift, units: .Metric)).hasSuffix(" · Ski lift"))
        #expect(state.canOpen(starred: stars[0]))
        #expect(!state.canOpen(starred: stars[1]))
        #expect(state.isRemoving(starred: stars[1]))
        #expect(!state.isRemoving(starred: stars[0]))
        #expect(state.showsEmpty)
        #expect(state.signInError == nil)
    }

    @Test func aSessionThatMustSignInAgainSaysSo() throws {
        let state = try #require(Samples.starredSegmentsVariants["needsSignIn"])
        let error = try #require(state.signInError)
        #expect(read(ErrorTexts.shared.of(error: error)).contains("Sign in again"))
        #expect(!state.showsEmpty)
        let failed = try #require(Samples.starredSegmentsVariants["unstarFailed"]?.actionError)
        #expect(!read(ErrorTexts.shared.of(error: failed)).isEmpty)
    }

    @Test func theLeaderboardOffersItsChoices() throws {
        let state = try #require(Samples.segmentLeaderboard["loaded"])
        #expect(labels(state.periodChoice) == ["All time", "This year", "This month"])
        #expect(labels(state.scopeChoice) == ["Everyone", "Following"])
        #expect(state.typeChoice == nil)
        #expect(read(state.ageGroupChoice.label) == "All ages")
        #expect(!state.ageGroupChoice.narrows)
        #expect(read(state.genderChoice.label) == "All genders")
    }

    @Test func aFilteredLeaderboardMarksItsBracketsInTheViewersUnits() throws {
        let state = try #require(Samples.segmentLeaderboardVariants["filtered"])
        let type = try #require(state.typeChoice)
        #expect(type.offersMenu)
        #expect(labels(type) == ["Ride", "E-bike"])
        #expect(read(type.label) == "Ride")
        #expect(read(state.ageGroupChoice.label) == "45–54")
        #expect(state.ageGroupChoice.narrows)
        #expect(read(state.weightClassChoice(units: .Metric).label) == "55–64 kg")
        let pounds = state.weightClassChoice(units: .Imperial)
        #expect(read(pounds.label) == "121–142 lb")
        #expect(labels(pounds)[1] == "Under 121 lb")
        #expect(state.viewerEntry?.rank == 140)
    }

    @Test func leaderboardEntriesReadTheirRankDayAndTime() throws {
        let state = try #require(Samples.segmentLeaderboardVariants["filtered"])
        let entries = state.entries.items.compactMap { $0 as? LeaderboardEntry }
        #expect(entries.map { read(RankBoardTexts.shared.rank(rank: $0.rank)) } == ["1", "2", "3", "4"])
        #expect(entries.map { state.accents(entry: $0) } == [true, true, true, false])
        #expect(entries.map { Stats.duration(seconds: $0.effort.elapsed) } == ["3:50", "4:22", "4:31", "1:02:05"])
        #expect(read(SegmentBoardTexts.shared.effortDay(entry: entries[0])) == "May 31, 2026")
    }

    @Test func aMayorsBoardReadsItsWindowTypeAndCounts() throws {
        let state = try #require(Samples.segmentMayorsVariants["filtered"])
        #expect(read(state.periodChoice.label) == "This week")
        #expect(labels(state.periodChoice).count == 8)
        #expect(labels(try #require(state.typeChoice)) == ["Hike", "Walk"])
        let entries = state.entries.items.compactMap { $0 as? MayorEntry }
        #expect(entries.map { state.accents(entry: $0) } == [true, false])
        #expect(read(SegmentBoardTexts.shared.efforts(entry: entries[0])) == "1,204 efforts")
        #expect(read(SegmentBoardTexts.shared.efforts(entry: entries[1])) == "9 efforts")
        #expect(read(SegmentBoardTexts.shared.reached(entry: entries[0])) == "Reached May 20, 2026")
        let own = try #require(state.viewerEntry)
        #expect(read(SegmentBoardTexts.shared.efforts(entry: own)) == "1 effort")
    }

    @Test func aRegularsBoardReadsItsWindowAndDives() throws {
        let state = try #require(Samples.diveSiteRegularsVariants["lastYear"])
        #expect(labels(state.periodChoice) == ["All time", "This year", "Last year"])
        #expect(read(state.periodChoice.label) == "Last year")
        let entries = state.entries.items.compactMap { $0 as? DiveSiteRegularEntry }
        #expect(entries.map { read(DiveSiteRegularsTexts.shared.visits(entry: $0)) } == ["31 dives", "17 dives"])
        #expect(entries.map { state.accents(entry: $0) } == [true, false])
        #expect(read(DiveSiteRegularsTexts.shared.reached(entry: entries[0])) == "Reached May 20, 2026")
        #expect(state.viewerEntry?.rank == 12)
    }
}
