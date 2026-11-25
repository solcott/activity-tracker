import SwiftUI

/// The activity page's figures and cards (feed/activity-page-layout), drawn from what Kotlin chose: the hero, the training load, a card
/// per metric (its headline, its lines as native charts, its zones or Set thresholds, its other figures, the laps table), the avg/max
/// table and the stats list, those two in All data. Every word is Kotlin's (`FigureTexts`, `CardLines`, `ZoneRows`, `LapTableTexts`), as the shared page's are.

/// A figure's value in the viewer's units with its unit, as Kotlin words it, read on the page's clock.
enum FigureValueText {
    static func string(_ value: any FigureValue, units: UnitSystem, clock: ShownClock) -> String {
        clock.string(FigureTexts.shared.value(value: value, units: units, now: clock.now, zone: clock.kotlinZone))
    }
}

/// A value's numbers larger than its unit ("7.83" larger than "mi"), in the runs Kotlin splits it into (`FigureParts`), which also keep a
/// narrow column from wrapping inside the unit ("1:59" over "/100 yd"), as the shared figure does.
enum FigureRuns {
    /// `value` with its numbers in `font` and the rest in `unitFont`; all in `font` when it holds no number (a device's name).
    static func text(_ value: String, font: Font, unitFont: Font) -> Text {
        let parts = FigureParts.shared.of(value: value)
        guard parts.contains(where: \.isNumber) else { return Text(verbatim: parts.map(\.text).joined()).font(font) }
        return parts.reduce(Text(verbatim: "")) { text, part in
            text + Text(verbatim: part.text).font(part.isNumber ? font : unitFont)
        }
    }
}

/// A label over its value, the value large with its unit smaller: the hero's and a card's headline.
struct FigureBlock: View {
    let figure: Figure
    var font: Font = .title2
    var unitFont: Font = .subheadline

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        let label = Text(FigureTexts.shared.label(figure: figure)).font(.caption).foregroundStyle(.secondary)
        VStack(alignment: .leading, spacing: 2) {
            // The badge beside the label when both fit the column, else under it, so neither is cut.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 6) {
                    label.fixedSize()
                    MarkBadge(mark: figure.mark).fixedSize()
                }
                VStack(alignment: .leading, spacing: 2) {
                    label.fixedSize(horizontal: false, vertical: true)
                    MarkBadge(mark: figure.mark).fixedSize()
                }
            }
            FigureRuns.text(
                FigureValueText.string(figure.value, units: stats.units, clock: clock), font: font.weight(.semibold), unitFont: unitFont
            )
            .monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// A label and its value on one row: the label wraps, the value stays whole at the trailing edge. A gear item's opens its page.
struct FigureRowView: View {
    let figure: Figure
    var openGear: ((String) -> Void)?

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        let value = FigureValueText.string(figure.value, units: stats.units, clock: clock)
        let row = HStack(alignment: .firstTextBaseline, spacing: 12) {
            HStack(spacing: 6) {
                Text(FigureTexts.shared.label(figure: figure))
                MarkBadge(mark: figure.mark)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(verbatim: value).fontWeight(.medium).multilineTextAlignment(.trailing).monospacedDigit()
        }
        .accessibilityElement(children: .combine)
        if let uri = FigureTexts.shared.gearUri(figure: figure), let openGear {
            Button {
                openGear(uri)
            } label: {
                row.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isButton)
        } else {
            row
        }
    }
}

/// A narrower category's badge (Followers, Mutuals, Only me) on the owner's page; nothing for none.
struct MarkBadge: View {
    let mark: String?

    var body: some View {
        if let badge = ActivityPageTexts.shared.mark(mark: mark) {
            Text(badge)
                .font(.caption2)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color(.secondarySystemFill), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                .accessibilityLabel(ActivityPageTexts.shared.markDescription(mark: mark).map { Text($0) } ?? Text(badge))
        }
    }
}

/// The hero, as the shared `HeroRow` lays it out: three figures or fewer on one row, set larger, so a short page reads as a short post;
/// four two by two, so none sits alone on a row; more three to a row. A label wraps rather than being cut.
struct HeroFigures: View {
    let figures: [Figure]

    var body: some View {
        let few = figures.count <= 3
        let columns = few ? max(figures.count, 1) : figures.count == 4 ? 2 : 3
        Grid(alignment: .topLeading, horizontalSpacing: 12, verticalSpacing: 12) {
            ForEach(Array(stride(from: 0, to: figures.count, by: columns)), id: \.self) { start in
                GridRow {
                    ForEach(start..<min(start + columns, figures.count), id: \.self) { index in
                        FigureBlock(figure: figures[index], font: few ? .title : .title3, unitFont: few ? .headline : .footnote)
                    }
                    ForEach(0..<(columns - min(columns, figures.count - start)), id: \.self) { _ in Color.clear.frame(height: 1) }
                }
            }
        }
    }
}

/// One metric's card as a section with its title as the header, as an import's preview shows it, everything showing (`MetricCardRows`).
struct MetricCardSection: View {
    let card: MetricCard
    let charts: ActivityCharts
    let missingZones: Bool
    var caption: (any AppText)?
    let setThresholds: () -> Void

    var body: some View {
        Section {
            MetricCardRows(card: card, charts: charts, missingZones: missingZones, caption: caption, setThresholds: setThresholds)
        } header: {
            HStack(spacing: 8) {
                Text(FigureTexts.shared.cardTitle(card: card))
                MarkBadge(mark: card.mark)
            }
            .accessibilityAddTraits(.isHeader)
        }
    }
}

/// An open card's rows under its title: on a combined activity the devices its data came from, its headline, each of its charts as
/// Kotlin builds them (`CardLines`: the elevation behind an effort line, a pair's two legs, every tank, a dive's marks and ceiling, the
/// power curve, the gears), its zones (or Set thresholds for the owner who has none), its other figures as rows, and its laps, legs,
/// lengths or sets table: on the activity page its first `maxRows` rows, then Show all (`showAll`).
struct MetricCardRows: View {
    let card: MetricCard
    let charts: ActivityCharts
    let missingZones: Bool
    var caption: (any AppText)?
    /// How many of its headline figures to leave out: the activity page's header row (`CardHeaderRow`) shows the first two.
    var shownHeadline: Int = 0
    var maxRows: Int = .max
    var showAll: (any AppText)?
    var openAll: () -> Void = {}
    let setThresholds: () -> Void

    @Environment(\.stats) private var stats

    var body: some View {
        let headline = Array(card.headline.elements(as: Figure.self).dropFirst(shownHeadline))
        let lines = CardChartsCache.lines(card, charts, stats.units)
        let zones = ZoneTimeRows.shared.of(card: card)
        if let caption {
            Text(caption).font(.footnote).foregroundStyle(.secondary)
        }
        if !headline.isEmpty {
            HStack(alignment: .top, spacing: 12) {
                ForEach(Array(headline.enumerated()), id: \.offset) { _, figure in
                    FigureBlock(figure: figure, font: .title3, unitFont: .footnote)
                }
            }
        }
        ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
            VStack(alignment: .leading, spacing: 8) {
                if line.title != nil || line.detail != nil {
                    HStack(alignment: .firstTextBaseline) {
                        if let title = line.title { Text(title).font(.subheadline.weight(.semibold)) }
                        Spacer(minLength: 8)
                        if let detail = line.detail { Text(detail).font(.caption).foregroundStyle(.secondary) }
                    }
                }
                LineChartView(chart: line.chart)
            }
            .padding(.vertical, 4)
        }
        if !zones.isEmpty {
            ZoneTimesView(heading: ZoneTimeRows.shared.heading(section: card.section), rows: zones)
        } else if missingZones, card.section == .HeartRate || card.section == .Power {
            HStack(spacing: 12) {
                Text(ZoneTimeRows.shared.missing).font(.footnote).foregroundStyle(.secondary).frame(
                    maxWidth: .infinity, alignment: .leading)
                Button(action: setThresholds) { Text(ZoneTimeRows.shared.setThresholds) }.buttonStyle(.borderless)
            }
        }
        ForEach(Array(card.rows.elements(as: Figure.self).enumerated()), id: \.offset) { _, figure in FigureRowView(figure: figure) }
        if let table = StructureTableTexts.shared.of(section: card.section, charts: charts, units: stats.units) {
            LapTableView(table: table, maxRows: maxRows)
        }
        if let showAll {
            Button(action: openAll) { Text(showAll) }
        }
    }
}

/// A card's first row on the activity page (feed/activity-page-layout), the same height open or closed so a list animating the change
/// never draws its two states out of line: its title, its first headline figure with the second muted under it, and a chevron that
/// turns; while it is closed (a strip, as the shared `MetricStrip` draws it) also its sparkline (`CardLines.spark`) and its zones as a
/// thin bar, which fade as it opens. A tap toggles it (`toggle`); a table, always open, has none.
struct CardHeaderRow: View {
    let card: MetricCard
    let charts: ActivityCharts
    let open: Bool
    let toggle: (() -> Void)?

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        if let toggle {
            Button(action: toggle) { row.contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: spoken))
                .accessibilityHint(Text(FigureTexts.shared.cardToggle(open: open)))
                .accessibilityAddTraits(open ? [.isButton, .isHeader] : .isButton)
        } else {
            row.accessibilityElement(children: .combine).accessibilityAddTraits(.isHeader)
        }
    }

    private var row: some View {
        let headline = card.headline.elements(as: Figure.self)
        let spark = CardChartsCache.spark(card, charts, stats.units)
        let zones = card.zones == nil ? [] : ZoneTimeRows.shared.of(card: card)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(FigureTexts.shared.cardTitle(card: card)).font(.headline)
                    MarkBadge(mark: card.mark)
                }
                figures(headline)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if spark != nil || !zones.isEmpty {
                VStack(spacing: 4) {
                    if let spark {
                        LineChartView(chart: spark, height: 28, interactive: false)
                    }
                    if !zones.isEmpty {
                        ZoneStripBar(fractions: zones.map { CGFloat($0.fraction) })
                    }
                }
                .frame(width: 104)
                // Kept in the layout, so the title and figures don't move as it fades.
                .opacity(open ? 0 : 1)
                .accessibilityHidden(open)
            }
            if toggle != nil {
                Image(.chevronDown)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(open ? 180 : 0))
            }
        }
    }

    /// The first headline's value large with its label after it, then the second's label and value under them, muted.
    @ViewBuilder private func figures(_ headline: [Figure]) -> some View {
        if let first = headline.first {
            // The first two, as the strip shows them; an open card lists any more under this row (`MetricCardRows.shownHeadline`).
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    FigureRuns.text(value(first), font: .title3.weight(.semibold), unitFont: .footnote)
                        .monospacedDigit()
                        .fixedSize()
                    Text(FigureTexts.shared.label(figure: first)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                if headline.count > 1 {
                    let second = headline[1]
                    (Text(FigureTexts.shared.label(figure: second)) + Text(verbatim: " ") + Text(verbatim: value(second)))
                        .font(.caption).foregroundStyle(.secondary).monospacedDigit().lineLimit(1)
                }
            }
        }
    }

    private func value(_ figure: Figure) -> String {
        FigureValueText.string(figure.value, units: stats.units, clock: clock)
    }

    /// The row read as one sentence: its title, then each headline figure's label and value.
    private var spoken: String {
        let title = AppStrings.string(FigureTexts.shared.cardTitle(card: card))
        let figures = card.headline.elements(as: Figure.self).prefix(2).map {
            "\(AppStrings.string(FigureTexts.shared.label(figure: $0))) \(value($0))"
        }
        return ([title] + figures).joined(separator: ", ")
    }
}

/// Time in zones as one thin bar, each zone's share in its colour (`ZoneColors`), lowest first, as the shared `ZoneStrip` draws it.
struct ZoneStripBar: View {
    let fractions: [CGFloat]

    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 1) {
                ForEach(Array(fractions.enumerated()), id: \.offset) { index, fraction in
                    if fraction > 0 {
                        Rectangle()
                            .fill(ZoneColors.color(index, of: fractions.count))
                            .frame(width: max(1, geometry.size.width * fraction - 1))
                    }
                }
            }
        }
        .frame(height: 4)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

/// Time in heart rate or power zones, hottest first as the shared `ZoneBars` lists them: each zone's label, name and range over a bar of
/// its share in the zone's colour (`ZoneColors`), its time and percent at the end; each read as Kotlin's sentence.
struct ZoneTimesView: View {
    let heading: any AppText
    let rows: [ZoneRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(heading).font(.subheadline.weight(.semibold))
            ForEach(Array(rows.enumerated().reversed()), id: \.offset) { index, row in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(row.label).font(.footnote.weight(.semibold))
                        Text(row.name).font(.footnote)
                        Text(row.range).font(.caption).foregroundStyle(.secondary)
                        Spacer(minLength: 8)
                        Text(row.time).font(.footnote.weight(.medium)).monospacedDigit()
                        Text(row.percent).font(.caption).foregroundStyle(.secondary).monospacedDigit()
                    }
                    GeometryReader { geometry in
                        Capsule().fill(Color(.secondarySystemFill))
                            .overlay(alignment: .leading) {
                                Capsule().fill(ZoneColors.color(index, of: rows.count))
                                    .frame(width: max(4, geometry.size.width * row.fraction))
                            }
                    }
                    .frame(height: 6)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(row.spoken))
            }
        }
        .padding(.vertical, 4)
    }
}

/// A card's table (laps, legs, lengths or sets): a header row naming each column and its unit, then a row per lap, each read as one
/// sentence. Cells are bare numbers at one size. The columns spread across the row's width when each has at least `minColumn`
/// points; on a narrower row (a phone's laps) the table scrolls sideways instead of shrinking or wrapping its numbers.
struct LapTableView: View {
    let table: DetailTable
    /// How many rows it shows: the activity page shows the first few (`TABLE_PREVIEW_ROWS`), All data all of them.
    var maxRows: Int = .max

    /// The narrowest a column is drawn.
    static let minColumn: CGFloat = 64
    private static let spacing: CGFloat = 8

    @State private var width: CGFloat = 0

    var body: some View {
        let headers = table.headers.elements(as: (any AppText).self)
        let needed = CGFloat(headers.count) * Self.minColumn + CGFloat(max(headers.count - 1, 0)) * Self.spacing
        Group {
            if width >= needed {
                grid(headers, spread: true)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    grid(headers, spread: false)
                }
                .sidewaysEdgeFades()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) {
            $0.size.width
        } action: {
            width = $0
        }
        .padding(.vertical, 4)
    }

    private func grid(_ headers: [any AppText], spread: Bool) -> some View {
        let rows = Array(table.rows.elements(as: TableRow.self).prefix(maxRows))
        let wide = Int(table.wideColumn)
        return Grid(alignment: .leading, horizontalSpacing: Self.spacing, verticalSpacing: 6) {
            GridRow {
                ForEach(Array(headers.enumerated()), id: \.offset) { _, header in
                    cell(Text(header).font(.caption2).foregroundStyle(.secondary), spread: spread, wraps: spread)
                }
            }
            .accessibilityHidden(true)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    ForEach(Array(row.cells.elements(as: (any AppText).self).enumerated()), id: \.offset) { column, value in
                        // A name may wrap to a second line; a number stays whole on one.
                        cell(Text(value).font(.footnote).monospacedDigit(), spread: spread, wraps: column == wide)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(row.spoken))
            }
        }
    }

    /// A cell at least `minColumn` wide, sharing what's left of the row when `spread`.
    @ViewBuilder private func cell(_ text: some View, spread: Bool, wraps: Bool) -> some View {
        if wraps {
            text.lineLimit(2).fixedSize(horizontal: false, vertical: true)
                .frame(minWidth: Self.minColumn, maxWidth: spread ? .infinity : 140, alignment: .leading)
        } else {
            text.lineLimit(1).fixedSize()
                .frame(minWidth: Self.minColumn, maxWidth: spread ? .infinity : nil, alignment: .leading)
        }
    }
}

/// The avg/max table, in All data: a row per metric with its average and maximum, and a Min column when a row has a minimum.
struct AvgMaxSection: View {
    let rows: [AvgMaxRow]
    var anchor: PageAnchor?

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    init(rows: [AvgMaxRow], anchor: PageAnchor? = nil) {
        self.rows = rows
        self.anchor = anchor
    }

    init(content: ActivityPageContent) {
        self.init(rows: content.avgMax.elements(as: AvgMaxRow.self))
    }

    var body: some View {
        if !rows.isEmpty {
            let texts = FigureTexts.shared
            let showsMin = texts.showsMin(rows: rows)
            Section {
                Grid(alignment: .trailing, horizontalSpacing: 12, verticalSpacing: 8) {
                    GridRow {
                        Color.clear.frame(height: 1).gridCellUnsizedAxes(.vertical)
                        Text(texts.avgColumn)
                        if showsMin { Text(texts.minColumn) }
                        Text(texts.maxColumn)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        GridRow {
                            Text(texts.avgMax(metric: row.metric)).frame(maxWidth: .infinity, alignment: .leading)
                            Text(verbatim: text(row.avg))
                            if showsMin { Text(verbatim: row.min.map { text($0) } ?? "") }
                            Text(verbatim: text(row.max))
                        }
                        .font(.subheadline)
                        .monospacedDigit()
                    }
                }
                .anchored(anchor)
            } header: {
                Text(texts.avgMaxHeading)
            }
        }
    }

    private func text(_ value: (any FigureValue)?) -> String {
        guard let value else { return AppStrings.string(StatText.shared.missing) }
        return FigureValueText.string(value, units: stats.units, clock: clock)
    }
}

/// The training load card, for a viewer served it: its figures as rows.
struct TrainingLoadSection: View {
    let figures: [Figure]

    var body: some View {
        if !figures.isEmpty {
            Section {
                ForEach(Array(figures.enumerated()), id: \.offset) { _, figure in FigureRowView(figure: figure) }
            } header: {
                Text(FigureTexts.shared.trainingLoad)
            }
        }
    }
}

/// Every figure, a group at a time under its name; a gear item's row opens its page.
struct StatsSections: View {
    let groups: [StatsGroup]
    /// All data's mark on its first row, for its chips.
    var anchor: PageAnchor?
    let openGear: (String) -> Void

    var body: some View {
        ForEach(Array(groups.enumerated()), id: \.offset) { index, group in
            Section {
                ForEach(Array(group.rows.elements(as: Figure.self).enumerated()), id: \.offset) { row, figure in
                    FigureRowView(figure: figure, openGear: openGear).anchored(index == 0 && row == 0 ? anchor : nil)
                }
            } header: {
                VStack(alignment: .leading, spacing: 4) {
                    // The list's heading goes over its first group.
                    if index == 0 { Text(FigureTexts.shared.allStats).font(.headline).foregroundStyle(.primary).textCase(nil) }
                    Text(FigureTexts.shared.group(kind: group.kind))
                }
            }
        }
    }
}

/// A card's charts as Kotlin makes them (`CardLines`), kept while its card and charts are the same objects: the presenter keeps both across
/// states (`rememberPage`), so a page's update, such as a card opening, reuses them rather than formatting every point's readout again,
/// and each `LineChartView` sees the same chart and isn't drawn again.
@MainActor
enum CardChartsCache {
    private struct Entry<Value> {
        let card: MetricCard
        let charts: ActivityCharts
        let units: String
        let value: Value
    }

    private static var lines: [ObjectIdentifier: Entry<[CardLine]>] = [:]
    private static var sparks: [ObjectIdentifier: Entry<LineChart?>] = [:]
    /// More than any page's cards, so a page keeps all of its own; past it the oldest page's are dropped with the rest.
    private static let limit = 64

    static func lines(_ card: MetricCard, _ charts: ActivityCharts, _ units: UnitSystem) -> [CardLine] {
        cached(&lines, card, charts, units) { CardLines.shared.of(card: card, charts: charts, units: units) }
    }

    static func spark(_ card: MetricCard, _ charts: ActivityCharts, _ units: UnitSystem) -> LineChart? {
        cached(&sparks, card, charts, units) { CardLines.shared.spark(card: card, charts: charts, units: units) }
    }

    private static func cached<Value>(
        _ store: inout [ObjectIdentifier: Entry<Value>], _ card: MetricCard, _ charts: ActivityCharts, _ units: UnitSystem,
        make: () -> Value
    ) -> Value {
        let unit = "\(units)"
        // The entry holds its card, so the identifier can't be reused by another while it is kept.
        if let entry = store[ObjectIdentifier(card)], entry.charts === charts, entry.units == unit { return entry.value }
        if store.count >= limit { store.removeAll() }
        let value = make()
        store[ObjectIdentifier(card)] = Entry(card: card, charts: charts, units: unit, value: value)
        return value
    }
}
