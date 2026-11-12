import MapKit
import Testing

@testable import ActivityTracker

/// What the interactive maps say and how the browse map converts MapKit's geometry to Kotlin's (segments/segment-browse,
/// segments/segment-map-viewer, dev/shared-display-text).
@MainActor
struct InteractiveMapsTextTests {
    @Test func aRegionIsReportedAsItsBoxAndCentre() {
        let region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 44.475, longitude: -73.21),
            span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.02))
        let viewport = SegmentsMapGeometry.viewport(region)
        #expect(viewport.minLat == 444_700_000)
        #expect(viewport.maxLat == 444_800_000)
        #expect(viewport.minLng == -732_200_000)
        #expect(viewport.maxLng == -732_000_000)
        #expect(viewport.centerLat == 444_750_000)
    }

    @Test func aTapsReachIsTheGroundDistanceToAFingersWidth() {
        let a = CLLocationCoordinate2D(latitude: 44.475, longitude: -73.21)
        let b = CLLocationCoordinate2D(latitude: 44.475 + 0.0001, longitude: -73.21)
        #expect(abs(SegmentsMapGeometry.meters(from: a, to: b) - 11.1) < 0.2)
    }

    @Test func browseListsTheSamplesRunsWithTheirDetails() throws {
        let state = try #require(Samples.segmentBrowse["loaded"])
        #expect(state.listShows(mapSupported: true) == .Area)
        #expect(state.areaRuns.count == 3)
        let details = AppStrings.string(SegmentBrowseTexts.shared.details(run: state.areaRuns[0], units: .Metric))
        #expect(details.hasSuffix("away"))
        #expect(details.contains(" · "))
    }

    @Test func aTapOnTheMapSelectsThroughKotlin() throws {
        let state = try #require(Samples.segmentBrowse["selected"])
        #expect(state.segmentsMap.drawn.elements(as: MapSegment.self).last?.role == .Selected)
        #expect(state.isSelected(segment: state.areaRuns[1].segment))
    }

    @Test func anOsmCreditLinksAndNamesItsLicence() throws {
        let state = try #require(Samples.segmentMap["credited"])
        let content = try #require(state.content.data as? SegmentMapContent)
        let credit = try #require(content.credit)
        #expect(SegmentCreditTexts.shared.links(credit: credit))
        #expect(SegmentCreditTexts.shared.lines(credit: credit).map(AppStrings.string) == ["© OpenStreetMap contributors", "Data licence: ODbL-1.0"])
        #expect(content.routeMap != nil)
    }

    @Test func acceptingHoldsTheFormAndSaysSo() throws {
        let state = try #require(Samples.acceptInvite["accepting"])
        #expect(state.busy)
        #expect(AppStrings.string(AcceptInviteTexts.shared.accept(accepting: state.accepting)) == "Accepting…")
        #expect(state.routeMap != nil)
        #expect(try #require(Samples.acceptInvite["signInAgain"]).offersSignInAgain)
    }
}
