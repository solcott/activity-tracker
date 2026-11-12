import Charts
import SwiftUI

/// A line chart drawn with Swift Charts from Kotlin's `LineChart` (`:app-common-api`), the value the shared chart draws too: its points
/// over its x range, the line filled beneath, the y axis Kotlin chose with a labelled gridline at each step, Kotlin's labels along the
/// bottom, no line or fill across a gap, and the bands it is tinted by (an elevation profile's grade, `GradeColors`). Tapping or dragging
/// sideways across it marks the nearest point and shows Kotlin's readout for it over the chart, staying where the finger left it; a
/// mostly vertical drag scrolls the page instead and hides it (`ChartScrub`). VoiceOver reads it as one element, Kotlin's description.
///
/// Everything it shows is the value's; this only lays it out, so a page passes a chart and a height and nothing else.
struct LineChartView: View {
    let chart: LineChart
    let height: CGFloat

    @State private var selected: Int32?
    @State private var scrub = ChartScrub()

    /// `selected` marks a point from the start, for a gallery or snapshot showing the readout.
    init(chart: LineChart, height: CGFloat = 140, selected: Int32? = nil) {
        self.chart = chart
        self.height = height
        _selected = State(initialValue: selected)
    }

    var body: some View {
        let points = chart.points.elements(as: LinePoint.self)
        let pieces = ChartPieces.of(points)
        let axis = chart.axis
        let labels = ChartLabels(chart: chart)
        let style = ChartTint(chart: chart)
        Chart {
            ForEach(Array(pieces.enumerated()), id: \.offset) { index, piece in
                ForEach(Array(piece.enumerated()), id: \.offset) { _, point in
                    AreaMark(
                        x: .value("x", point.x), yStart: .value("y", axis.min), yEnd: .value("y", point.y),
                        series: .value("piece", index)
                    )
                    .foregroundStyle(style.area)
                    LineMark(x: .value("x", point.x), y: .value("y", point.y), series: .value("piece", index))
                        .foregroundStyle(style.line)
                        .lineStyle(StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                }
            }
            // The bottom axis's line, which the shared chart draws as its baseline.
            RuleMark(y: .value("y", axis.min)).foregroundStyle(Color(.separator)).lineStyle(StrokeStyle(lineWidth: 1))
            if let selected, Int(selected) < points.count {
                let point = points[Int(selected)]
                RuleMark(x: .value("x", point.x)).foregroundStyle(Color(.secondaryLabel)).lineStyle(StrokeStyle(lineWidth: 1))
                PointMark(x: .value("x", point.x), y: .value("y", point.y))
                    .foregroundStyle(Color.accentColor)
                    .symbolSize(48)
                    .annotation(position: .top, spacing: 6, overflowResolution: .init(x: .fit(to: .chart), y: .fit(to: .chart))) {
                        ChartReadoutLabel(text: AppStrings.string(chart.readouts.elements(as: (any AppText).self)[Int(selected)]))
                    }
            }
        }
        // Half a label of room at each end, as the shared chart keeps, so a label at either end isn't cut off.
        .chartXScale(domain: chart.xMin...max(chart.xMax, chart.xMin + 1e-9), range: .plotDimension(startPadding: 18, endPadding: 18))
        .chartYScale(domain: axis.min...axis.max)
        .chartYAxis {
            AxisMarks(position: .leading, values: labels.yValues) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 1)).foregroundStyle(Color(.label).opacity(0.05))
                AxisValueLabel { Text(verbatim: labels.y(value.as(Double.self))).font(.caption2).foregroundStyle(.secondary) }
            }
        }
        .chartXAxis {
            AxisMarks(values: labels.xValues) { value in
                AxisValueLabel(anchor: .top, collisionResolution: .disabled) {
                    Text(verbatim: labels.x(value.as(Double.self))).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geometry in
                Rectangle().fill(.clear).contentShape(Rectangle())
                    .simultaneousGesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { drag in
                                let shows =
                                    drag.translation == .zero
                                    ? scrub.press(at: drag.startLocation) : scrub.move(to: drag.location)
                                select(shows ? drag.location : nil, proxy: proxy, geometry: geometry)
                            }
                            .onEnded { _ in
                                if !scrub.release() { selected = nil }
                            }
                    )
            }
        }
        .frame(height: height)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(chart.description))
    }

    /// Marks the point nearest the finger at `location`, or none.
    private func select(_ location: CGPoint?, proxy: ChartProxy, geometry: GeometryProxy) {
        guard let location, let plot = proxy.plotFrame else {
            selected = nil
            return
        }
        let origin = geometry[plot].origin
        guard let x: Double = proxy.value(atX: location.x - origin.x) else { return }
        selected = chart.nearest(x: x)
    }
}

/// The readout over a chart: Kotlin's text for the marked point, on the page's background with a hairline.
private struct ChartReadoutLabel: View {
    let text: String

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        Text(verbatim: text)
            .font(.caption.weight(.medium))
            .foregroundStyle(Color(.label))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(.systemBackground), in: shape)
            .overlay(shape.strokeBorder(Color(.separator), lineWidth: 0.5))
            .fixedSize()
    }
}

/// A chart's points split where the samples have gaps, so each piece is a series of its own and nothing is drawn across a gap.
enum ChartPieces {
    static func of(_ points: [LinePoint]) -> [[LinePoint]] {
        var pieces: [[LinePoint]] = []
        for point in points {
            if point.gapBefore || pieces.isEmpty { pieces.append([]) }
            pieces[pieces.count - 1].append(point)
        }
        return pieces.filter { !$0.isEmpty }
    }
}

/// The y and x tick labels in the catalog's words, looked up by the value Swift Charts asks about.
private struct ChartLabels {
    let yValues: [Double]
    let xValues: [Double]
    private let yTexts: [String]
    private let xTexts: [String]

    init(chart: LineChart) {
        let y = chart.yTicks.elements(as: ChartTick.self)
        let x = chart.xTicks.elements(as: ChartTick.self)
        yValues = y.map(\.value)
        xValues = x.map(\.value)
        yTexts = y.map { AppStrings.string($0.label) }
        xTexts = x.map { AppStrings.string($0.label) }
    }

    func y(_ value: Double?) -> String { Self.text(value, values: yValues, texts: yTexts) }
    func x(_ value: Double?) -> String { Self.text(value, values: xValues, texts: xTexts) }

    private static func text(_ value: Double?, values: [Double], texts: [String]) -> String {
        guard let value, let index = values.firstIndex(where: { abs($0 - value) < 1e-6 }) else { return "" }
        return texts[index]
    }
}

/// How a chart's line and area are painted: the accent colour, or across the chart by its bands.
private struct ChartTint {
    let line: AnyShapeStyle
    let area: AnyShapeStyle

    init(chart: LineChart) {
        let stops = (0..<chart.bandCount).map { chart.bandAt(index: $0) }
        if stops.isEmpty {
            line = AnyShapeStyle(Color.accentColor)
            area = AnyShapeStyle(Color.accentColor.opacity(0.18))
        } else {
            let bands = stops.map { ChartBands.Stop(at: Double($0.at), band: Int($0.band)) }
            line = AnyShapeStyle(ChartBands.gradient(bands, colors: GradeColors.all))
            area = AnyShapeStyle(ChartBands.gradient(bands, colors: GradeColors.all.map { $0.opacity(0.45) }))
        }
    }
}

/// A horizontal gradient across a chart by its band stops: each band's colour held to the next stop and switching sharply there, as
/// the shared chart's brush paints it.
enum ChartBands {
    struct Stop: Equatable {
        let at: Double
        let band: Int
    }

    /// The gradient's stops: each change repeats its place, ending one colour and starting the next exactly there.
    static func stops(_ stops: [Stop], count: Int) -> [(location: Double, band: Int)] {
        guard let last = stops.last else { return [(0, 0), (1, 0)] }
        func band(_ value: Int) -> Int { min(max(value, 0), count - 1) }
        var result: [(location: Double, band: Int)] = []
        for (index, stop) in stops.enumerated() {
            if index > 0 { result.append((stop.at, band(stops[index - 1].band))) }
            result.append((stop.at, band(stop.band)))
        }
        result.append((1, band(last.band)))
        return result
    }

    static func gradient(_ bands: [Stop], colors: [Color]) -> LinearGradient {
        let stops = stops(bands, count: colors.count).map { Gradient.Stop(color: colors[$0.band], location: $0.location) }
        return LinearGradient(stops: stops, startPoint: .leading, endPoint: .trailing)
    }
}

/// When a press on a chart shows its readout: wherever a finger taps or drags across it, staying after it lifts so the value can be
/// read. A drag that starts mostly vertical, past `slop` points, is the page scrolling, so it hides the readout instead.
struct ChartScrub {
    var slop: CGFloat = 10
    private var pressedAt: CGPoint?
    private var scrolling = false

    /// A finger went down at `point`: the readout shows.
    mutating func press(at point: CGPoint) -> Bool {
        pressedAt = point
        scrolling = false
        return true
    }

    /// The finger moved to `point`: the readout shows unless the drag became the page's scroll.
    mutating func move(to point: CGPoint) -> Bool {
        if pressedAt == nil { pressedAt = point }
        if let start = pressedAt, !scrolling {
            let dx = abs(point.x - start.x)
            let dy = abs(point.y - start.y)
            if dy > slop && dy > dx { scrolling = true }
        }
        return !scrolling
    }

    /// The finger lifted: whether the readout stays.
    mutating func release() -> Bool {
        pressedAt = nil
        return !scrolling
    }
}

/// The five grade bands' colours, from flattest to steepest, as the shared chart's (`GradeBands`): darker on a light surface and
/// lighter on a dark one.
enum GradeColors {
    static let all: [Color] = [
        Color(.gradeBand0), Color(.gradeBand1), Color(.gradeBand2), Color(.gradeBand3), Color(.gradeBand4),
    ]
}

#if DEBUG
    #Preview("Line chart") {
        List { LineChartView(chart: Samples.profileChart) }
    }
#endif
