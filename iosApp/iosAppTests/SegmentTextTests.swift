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
        let figures = state.figures(units: .Metric).map { "\(read($0.value) ?? "") \(read($0.label) ?? "")" }
        #expect(figures == ["1.60 km Distance", "84 m Gain", "4.5% Avg grade", "12% Max grade"])
        #expect(read(state.figures(units: .Metric)[1].detail) == "12 m loss")
        #expect(read(state.figures(units: .Imperial)[1].value) == "276 ft")
        let profile = try #require(state.profile)
        #expect(read(texts.extremes(profile: profile, units: .Metric)) == "1,504 m high · 1,420 m low")
        #expect(read(texts.heading(segment: segment)) == "Ride · Active")
        #expect(state.chart(units: .Metric) != nil)
        #expect(state.routeMap != nil)
        #expect(state.offersLeaderboard)
    }

    @Test func theOsmSegmentReadsItsBoardsTrailAndGroups() throws {
        let state = try #require(Samples.segmentVariants["openStreetMap"])
        let texts = SegmentPageTexts.shared
        let board = try #require(state.leaderboard)
        #expect(read(texts.leaderboardYours(entry: try #require(board.viewer))) == "You: 5:12 · #3")
        #expect(state.showsBoards)
        #expect(state.shownTrails.map(\.name) == ["Notch Road"])
        #expect(state.trailGroups().map { read($0.title) } == ["Difficulty", "Surface & width", "Access & season"])
    }

    @Test func aRideSegmentReadsItsTrailDetails() throws {
        let state = try #require(Samples.segment["trail"])
        let rows = state.trailDetails()
        let read = rows.map { "\(AppStrings.string($0.label)): \(AppStrings.string($0.value))" }
        #expect(read.contains("Surface: 70% paved · 30% gravel · Rough"))
        #expect(read.contains("MTB: Intermediate: roots, rocks and small steps"))
        #expect(read.contains("Width: Singletrack"))
        #expect(read.contains("Dogs: Dogs on leash"))
        #expect(read.contains("Season: Open Jun 15 – Oct 15"))
        #expect(!read.contains { $0.hasPrefix("Hiking:") })
        #expect(rows.last?.link == "https://kingdomtrails.org")
        #expect(TrailLink.url("javascript:alert(1)") == nil)
        #expect(TrailLink.url("https://kingdomtrails.org") != nil)
    }

    @Test func aLoneOneWaySegmentReadsUphillOnly() throws {
        let group = try #require(Samples.segmentVariants["oneWay"]?.group)
        #expect(group.offered && group.oneWay && !group.showsSports)
        #expect(read(GroupSwitcherTexts.shared.oneWay(direction: try #require(group.directions.first))) == "Uphill only")
    }

    @Test func aClimbingGroupReadsUphillAndDownhill() throws {
        let group = try #require(Samples.segmentVariants["groupByClimb"]?.group)
        #expect(Set(group.directions.map { read(GroupSwitcherTexts.shared.direction(direction: $0)) }) == ["Uphill", "Downhill"])
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
