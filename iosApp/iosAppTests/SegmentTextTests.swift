import Testing

@testable import ActivityTracker

/// What the segment page and an effort row say (segments/segment-efforts, segments/segment-elevation, dev/shared-display-text). The
/// rules are Kotlin's, with their own tests; these hold that Swift reads each on the shared samples, typed, and the catalog words each
/// as the shared page does.
@MainActor
struct SegmentTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func aSegmentWithElevationReadsItsLines() throws {
        let state = try #require(Samples.segment["elevation"])
        let segment = try #require(state.segment)
        let texts = SegmentPageTexts.shared
        #expect(read(texts.elevation(segment: segment, units: .Metric)) == "84 m gain · 12 m loss · 4.5% avg grade")
        #expect(read(texts.elevation(segment: segment, units: .Imperial)) == "276 ft gain · 39 ft loss · 4.5% avg grade")
        let profile = try #require(state.profile)
        #expect(read(texts.extremes(profile: profile, units: .Metric)) == "1,504 m high · 1,420 m low · 12% max grade")
        #expect(read(texts.summary(segment: segment, units: .Metric)) == "1.60 km · Ride")
        #expect(state.chart(units: .Metric) != nil)
        #expect(state.routeMap != nil)
        #expect(state.offersLeaderboard)
    }

    @Test func aLiftHasNoBoardsAndCountsRides() throws {
        let state = try #require(Samples.segment["lift"])
        let segment = try #require(state.segment)
        #expect(state.isLift)
        #expect(!state.offersLeaderboard)
        #expect(read(SegmentPageTexts.shared.efforts(lift: true)) == "Your rides")
        #expect(SegmentTexts.shared.kindIcon(sport: segment.record.sport, kind: segment.record.kind) == "ski_lift")
    }

    @Test func anOsmSegmentCreditsItsDataAndItsMayor() throws {
        let state = try #require(Samples.segmentVariants["openStreetMap"])
        let credit = try #require(state.credit)
        let texts = SegmentPageTexts.shared
        #expect(SegmentCreditTexts.shared.links(credit: credit))
        #expect(SegmentCreditTexts.shared.lines(credit: credit).map(read) == ["© OpenStreetMap contributors", "Data licence: ODbL-1.0"])
        let mayor = try #require(state.mayor)
        #expect(read(texts.mayorValue(entry: try #require(mayor.leader))) == "14 efforts")
        #expect(read(texts.mayorYours(entry: try #require(mayor.viewer))) == "You: 3 efforts")
        #expect(read(texts.star(starred: true)) == "Unstar segment")
    }

    @Test func aDuplicateFromAnAgencySaysWhatItIs() throws {
        let state = try #require(Samples.segmentVariants["duplicate"])
        let segment = try #require(state.segment)
        #expect(SegmentCreditTexts.shared.lines(credit: try #require(state.credit)).map(read).first == "From US Forest Service trail data")
        #expect(read(PlaceTexts.shared.status(status: segment.status)) == "Duplicate")
        #expect(SegmentPageTexts.shared.offersOriginal(segment: segment))
    }

    @Test func effortRowsReadTheirRankPrAndReasons() throws {
        let efforts = try #require(Samples.segmentVariants["openStreetMap"]).efforts.elements(as: EffortView.self)
        let texts = EffortTexts.shared
        #expect(read(texts.rank(effort: efforts[0])) == "3rd")
        #expect(read(texts.pr(effort: efforts[0])) == "PR")
        #expect(texts.time(effort: efforts[0]) == "5:12")
        #expect(read(texts.notRanked(effort: efforts[1])) == "Not on leaderboards: this activity is excluded")
        #expect(read(texts.eBike(effort: efforts[2])) == "E-bike")
        #expect(texts.time(effort: efforts[2]) == "1:02:05")
        #expect(read(texts.date(effort: efforts[0])) == "May 31, 2026")
    }
}
