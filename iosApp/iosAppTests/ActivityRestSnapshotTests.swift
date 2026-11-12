import SwiftUI
import Testing

@testable import ActivityTracker

/// The rest of the activity page against its references (feed/activity-page-layout, feed/route-map-viewer, feed/combined-activities,
/// ui/ios-design-system, dev/ios-snapshot-tests): the cards with layered charts, a dive's, the structure tables and a combined ride's
/// captions, the combination sheet, every layered chart on its own, and the enlarged route map, media gallery and combine picker in each
/// of their samples. Snapshots run outside a session, so maps draw their stand-ins and photos their placeholders.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ActivityRestSnapshotTests {
    nonisolated private static let pages = ["moreCharts", "dive", "structures", "combined"]
    nonisolated private static let routeMaps = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let galleries = [
        "loading", "failed", "loaded", "refreshing", "refreshFailed", "report", "zoomed", "blurred", "takenDown",
    ]
    nonisolated private static let pickers = ["loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "combining", "combineFailed"]
    private static let tall = CGSize(width: 393, height: 2_600)

    @Test(arguments: pages)
    func activityPage(sample: String) throws {
        let state = try #require(Samples.activityDetailVariants[sample])
        let page = NavigationStack { ActivityPage(state: state) }.environment(\.clock, Samples.clock)
        assertAppSnapshot(of: page, size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func layeredCharts() {
        assertAppSnapshot(of: DesignGallery.layeredCharts, size: CGSize(width: 393, height: 1_300), named: "layeredCharts")
    }

    @Test func editCombination() {
        assertAppSnapshot(
            of: EditCombinationSheet(page: Samples.combinedPage).environment(\.clock, Samples.clock), named: "editCombination",
            precision: Snapshots.barButtons)
    }

    @Test(arguments: routeMaps)
    func routeMap(sample: String) throws {
        let state = try #require(Samples.routeMap[sample])
        assertAppSnapshot(of: NavigationStack { ActivityRouteMapView(state: state) }, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: galleries)
    func mediaGallery(sample: String) throws {
        let state = try #require(Samples.mediaGallery[sample])
        assertAppSnapshot(of: NavigationStack { MediaGalleryView(state: state) }, named: sample)
    }

    @Test(arguments: pickers)
    func combinePicker(sample: String) throws {
        let state = try #require(Samples.combinePicker[sample])
        let picker = NavigationStack { CombinePickerView(state: state) }.environment(\.clock, Samples.clock)
        assertAppSnapshot(of: picker, named: sample, precision: Snapshots.barButtons)
    }
}

/// The rest of the activity page's rules, as Swift reads them (dev/shared-display-text).
struct ActivityRestTextTests {
    @Test func aDiveChartMarksItsSwitchAndWarning() throws {
        let charts = Samples.layeredCharts
        let marked = try #require(charts.first { $0.markCount > 0 })
        #expect(marked.markCount == 2)
        #expect(marked.fillAbove)
        #expect(marked.markAt(index: 1).warning)
    }

    @Test func otherLinesTakeTheNextColourInTurn() {
        #expect(ChartOtherColors.color(0) == ChartOtherColors.all[0])
        #expect(ChartOtherColors.color(4) == ChartOtherColors.all[1])
    }

    @Test func theStructurePageHasATablePerCard() throws {
        let state = try #require(Samples.activityDetailVariants["structures"])
        let page = try #require(state.content.data as? ActivityDetailPage)
        let charts = try #require(page.page.charts)
        for card in page.page.cards.elements(as: MetricCard.self) {
            #expect(StructureTableTexts.shared.of(section: card.section, charts: charts, units: .Metric) != nil)
        }
    }
}
