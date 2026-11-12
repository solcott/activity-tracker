import MapKit
import Testing

@testable import ActivityTracker

/// What the dive site page and a place's names say, and how the native map frames a route (dive-sites/site-records,
/// dive-sites/site-names, dev/shared-display-text, ui/ios-design-system). The rules are Kotlin's, with their own tests; these hold that
/// Swift reads each on the shared samples, typed, and the catalog words each as the shared page does.
@MainActor
struct DiveSiteTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private let utc = KotlinTimeZone.Companion.shared.of(zoneId: "UTC")

    @Test func anActiveSiteReadsItsAboutLinesAndConditions() throws {
        let state = try #require(Samples.diveSite["loaded"])
        let site = try #require(state.site)
        let texts = DiveSiteTexts.shared
        #expect(read(texts.createdBy(site: site)) == "By @alice.test")
        #expect(read(texts.radius(site: site, units: .Metric))?.hasPrefix("Within ") == true)
        #expect(texts.statusHelp(site: site) == nil)
        #expect(texts.osmCredit(site: site) == nil)
        #expect(read(texts.license(site: site)) == "Data licence: CC0-1.0")
        let conditions = try #require(state.conditions)
        let lines = texts.conditionLines(conditions: conditions, units: .Metric).map { read($0) }
        #expect(lines.count == 3)
        #expect(lines.first??.hasPrefix("Water 26.5") == true)
        #expect(texts.monthLines(conditions: conditions, units: .Metric).count == 2)
        let imperial = texts.conditionLines(conditions: conditions, units: .Imperial).map { read($0) }
        #expect(imperial.first??.contains("°F") == true)
        #expect(state.regulars?.leader != nil)
        #expect(state.routeMap != nil)
        #expect(state.routeMap?.start == nil && state.routeMap?.end == nil)
    }

    @Test func aProposedOsmSiteAndADuplicateSayWhatTheyAre() throws {
        let proposed = try #require(Samples.diveSite["proposed"]?.site)
        #expect(read(DiveSiteTexts.shared.osmCredit(site: proposed)) == "© OpenStreetMap contributors")
        #expect(read(DiveSiteTexts.shared.status(site: proposed)) == "Proposed")
        #expect(!DiveSiteTexts.shared.offersOriginal(site: proposed))
        let duplicate = try #require(Samples.diveSiteVariants["duplicate"]?.site)
        #expect(read(DiveSiteTexts.shared.status(site: duplicate)) == "Duplicate")
        #expect(DiveSiteTexts.shared.offersOriginal(site: duplicate))
    }

    @Test func namesReadTheirLinesAndTheVoteATapSends() throws {
        let state = try #require(Samples.diveSiteVariants["renamed"])
        let proposals = state.proposals.elements(as: SegmentNameProposalView.self)
        let history = state.history.elements(as: SegmentNameAdoption.self)
        let texts = PlaceNameTexts.shared
        let bobs = try #require(proposals.first)
        #expect(read(texts.proposedBy(proposal: bobs)) == "By @bob.test · 4 up · 1 down")
        #expect(texts.votedUp(proposal: bobs))
        #expect(texts.voteEvent(proposal: bobs, up: true).direction == nil)
        #expect(texts.voteEvent(proposal: bobs, up: false).direction == "down")
        #expect(state.names.isOwn(proposal: try #require(proposals.last)))
        #expect(read(texts.originally(original: try #require(state.originalName))) == "originally Ras Mohammed")
        let entry = try #require(history.first)
        #expect(read(texts.historyEntry(adoption: entry, zone: utc))?.contains("1,204 up, 3 down") == true)
        #expect(read(texts.historyBy(adoption: entry)) == "Proposed by @bob.test")
    }

    @Test func mapKitFramesWhatKotlinFrames() throws {
        let map = try #require(Samples.diveSite["loaded"]?.routeMap)
        let size = CGSize(width: 360, height: 240)
        let frame = map.frame(width: size.width, height: size.height, top: 0)
        let rect = MapCamera.rect(frame, size: size)
        let centre = rect.origin.coordinate(offsetBy: rect.size)
        #expect(abs(centre.latitude - frame.latitude) < 1e-9)
        #expect(abs(centre.longitude - frame.longitude) < 1e-9)
        // At Kotlin's zoom the view's width spans 360 points of a world 512 · 2^zoom points wide.
        let worldPoints = 512 * pow(2, frame.zoom)
        #expect(abs(rect.size.width / MKMapSize.world.width - 360 / worldPoints) < 1e-12)
        // The site's outline fits inside the rect with the shared margin.
        let bounds = map.bounds
        let northWest = MKMapPoint(CLLocationCoordinate2D(latitude: bounds.north, longitude: bounds.west))
        let southEast = MKMapPoint(CLLocationCoordinate2D(latitude: bounds.south, longitude: bounds.east))
        #expect(rect.contains(northWest) && rect.contains(southEast))
    }
}

private extension MKMapPoint {
    /// The coordinate at the centre of a rect starting here.
    func coordinate(offsetBy size: MKMapSize) -> CLLocationCoordinate2D {
        MKMapPoint(x: x + size.width / 2, y: y + size.height / 2).coordinate
    }
}
