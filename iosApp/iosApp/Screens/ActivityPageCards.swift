import SwiftUI

/// The activity page's figures and cards (feed/activity-page-layout), drawn from what Kotlin chose: the hero, the training load, a card
/// per metric (its headline, its lines as native charts, its zones or Set thresholds, its other figures, the laps table), the avg/max
/// table and the stats list. Every word is Kotlin's (`FigureTexts`, `CardLines`, `ZoneRows`, `LapTableTexts`), as the shared page's are.

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
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Text(FigureTexts.shared.label(figure: figure)).font(.caption).foregroundStyle(.secondary)
                MarkBadge(mark: figure.mark)
            }
            FigureRuns.text(FigureValueText.string(figure.value, units: stats.units, clock: clock), font: font.weight(.semibold), unitFont: unitFont)
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
            Button { openGear(uri) } label: { row.contentShape(Rectangle()) }
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

/// The hero: its figures two to a row, three where the row has room, each wrapping its label rather than cutting it.
struct HeroFigures: View {
    let figures: [Figure]

    var body: some View {
        ViewThatFits(in: .horizontal) {
            grid(columns: 3).frame(minWidth: 340)
            grid(columns: 2)
        }
    }

    private func grid(columns: Int) -> some View {
        Grid(alignment: .topLeading, horizontalSpacing: 12, verticalSpacing: 12) {
            ForEach(Array(stride(from: 0, to: figures.count, by: columns)), id: \.self) { start in
                GridRow {
                    ForEach(start..<min(start + columns, figures.count), id: \.self) { index in
                        FigureBlock(figure: figures[index])
                    }
                    ForEach(0..<(columns - min(columns, figures.count - start)), id: \.self) { _ in Color.clear.frame(height: 1) }
                }
            }
        }
    }
}

/// One metric's card as a section: its title (and lock) as the header, on a combined activity the devices its data came from, its
/// headline, each of its charts as Kotlin builds them (`CardLines`: the elevation behind an effort line, a pair's two legs, every tank, a
/// dive's marks and ceiling, the power curve, the gears), its zones (or Set thresholds for the owner who has none), its other figures as
/// rows, and its laps, legs, lengths or sets table.
struct MetricCardSection: View {
    let card: MetricCard
    let charts: ActivityCharts
    let missingZones: Bool
    var caption: (any AppText)?
    let setThresholds: () -> Void

    @Environment(\.stats) private var stats

    var body: some View {
        let headline = card.headline.elements(as: Figure.self)
        let lines = CardLines.shared.of(card: card, charts: charts, units: stats.units)
        let zones = ZoneTimeRows.shared.of(card: card)
        Section {
            if let caption {
                Text(caption).font(.footnote).foregroundStyle(.secondary)
            }
            if !headline.isEmpty {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(headline.enumerated()), id: \.offset) { _, figure in FigureBlock(figure: figure, font: .title3, unitFont: .footnote) }
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
                    Text(ZoneTimeRows.shared.missing).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                    Button(action: setThresholds) { Text(ZoneTimeRows.shared.setThresholds) }.buttonStyle(.borderless)
                }
            }
            ForEach(Array(card.rows.elements(as: Figure.self).enumerated()), id: \.offset) { _, figure in FigureRowView(figure: figure) }
            if let table = StructureTableTexts.shared.of(section: card.section, charts: charts, units: stats.units) {
                LapTableView(table: table)
            }
        } header: {
            HStack(spacing: 8) {
                Text(FigureTexts.shared.cardTitle(card: card))
                MarkBadge(mark: card.mark)
            }
            .accessibilityAddTraits(.isHeader)
        }
    }
}

/// Time in heart rate or power zones: each zone's label, name and range over a bar of its share, its time at the end; each one sentence.
struct ZoneTimesView: View {
    let heading: any AppText
    let rows: [ZoneRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(heading).font(.subheadline.weight(.semibold))
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(row.label).font(.footnote.weight(.semibold))
                        Text(row.name).font(.footnote)
                        Text(row.range).font(.caption).foregroundStyle(.secondary)
                        Spacer(minLength: 8)
                        Text(row.time).font(.footnote.weight(.medium)).monospacedDigit()
                    }
                    GeometryReader { geometry in
                        Capsule().fill(Color(.secondarySystemFill))
                            .overlay(alignment: .leading) {
                                Capsule().fill(Color.accentColor).frame(width: max(4, geometry.size.width * row.fraction))
                            }
                    }
                    .frame(height: 6)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.vertical, 4)
    }
}

/// A card's table (laps, legs, lengths or sets): a header row, then a row per lap, each read as one sentence, every cell on one line that
/// shrinks to fit but the table's wide column (a name), which wraps to a second.
struct LapTableView: View {
    let table: DetailTable

    var body: some View {
        let headers = table.headers.elements(as: (any AppText).self)
        let rows = table.rows.elements(as: TableRow.self)
        Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 6) {
            GridRow {
                ForEach(Array(headers.enumerated()), id: \.offset) { _, header in
                    Text(header).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
            .accessibilityHidden(true)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    ForEach(Array(row.cells.elements(as: (any AppText).self).enumerated()), id: \.offset) { column, cell in
                        // One line each, shrinking rather than wrapping, so a row of seven columns fits a phone; a name may wrap.
                        if column == Int(table.wideColumn) {
                            Text(cell).font(.footnote).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                        } else {
                            Text(cell).font(.footnote).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(row.spoken))
            }
        }
        .padding(.vertical, 4)
    }
}

/// The avg/max table: a row per metric with its average and maximum, and a Min column when a row has a minimum.
struct AvgMaxSection: View {
    let content: ActivityPageContent

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        let rows = content.avgMax.elements(as: AvgMaxRow.self)
        if !rows.isEmpty {
            let texts = FigureTexts.shared
            let showsMin = texts.showsMin(content: content)
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
    let openGear: (String) -> Void

    var body: some View {
        ForEach(Array(groups.enumerated()), id: \.offset) { index, group in
            Section {
                ForEach(Array(group.rows.elements(as: Figure.self).enumerated()), id: \.offset) { _, figure in
                    FigureRowView(figure: figure, openGear: openGear)
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
