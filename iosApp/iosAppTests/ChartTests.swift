import CoreGraphics
import Testing

@testable import ActivityTracker

/// How the native chart lays out Kotlin's chart (ui/ios-design-system): pieces at gaps, the bands' hard stops and when a drag shows the
/// readout. What the chart shows is Kotlin's, with its own tests; these hold that Swift reads it, typed.
@MainActor
struct ChartTests {
    @Test func aGapStartsANewPiece() {
        let points = Samples.plainChart.points.elements(as: LinePoint.self)
        let pieces = ChartPieces.of(points)
        #expect(pieces.count == 2)
        #expect(pieces.map(\.count).reduce(0, +) == points.count)
    }

    @Test func eachBandChangeRepeatsItsPlace() {
        let stops = ChartBands.stops([.init(at: 0, band: 0), .init(at: 0.5, band: 3)], count: 5)
        #expect(stops.map(\.location) == [0, 0.5, 0.5, 1])
        #expect(stops.map(\.band) == [0, 0, 3, 3])
        #expect(ChartBands.stops([.init(at: 0, band: 9)], count: 5).map(\.band) == [4, 4])
    }

    @Test func aSidewaysDragScrubsAndAVerticalOneScrolls() {
        #expect(ChartScrub.scrubs(velocity: CGPoint(x: 300, y: 40)))
        #expect(!ChartScrub.scrubs(velocity: CGPoint(x: 20, y: -500)))
    }

    @Test func theProfileReadsItsTicksAndReadoutsInTheCatalogsWords() {
        let chart = Samples.profileChart
        let ticks = chart.xTicks.elements(as: ChartTick.self).map { AppStrings.string($0.label) }
        #expect(ticks == ["0.0 km", "0.5 km", "1.0 km", "1.5 km"])
        #expect(chart.yTicks.elements(as: ChartTick.self).allSatisfy { AppStrings.string($0.label).hasSuffix(" m") })
        #expect(AppStrings.string(chart.readout(x: 0)).hasSuffix("grade"))
        #expect(chart.bandCount > 1)
        #expect(chart.nearest(x: 99) == Int32(chart.points.elements(as: LinePoint.self).count - 1))
    }
}
