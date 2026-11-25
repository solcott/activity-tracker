import SwiftUI

/// An activity's All data page (feed/activity-page-layout), pushed over the activity page on a phone: every figure in its groups, the
/// avg/max table, each table in full and a combined activity's sources, under chips for its sections, opening at the section it was
/// opened from. What it holds and every word are Kotlin's (`ActivityData`, `ActivityDataTexts`), as the shared page's are.
struct ActivityDataView: View {
    let state: ActivityDataState

    var body: some View {
        ContentStateView(state.content, as: ActivityData.self, errorKind: .LoadActivity, retry: retry) { data in
            ActivityDataList(data: data, openGear: { state.eventSink(ActivityDataEvent.OpenGear(uri: $0)) }) {
                RefreshStatusView(state: state.content, errorKind: .LoadActivity, retry: retry).listStatusRow()
            }
        }
        .pageTitle(Text(ActivityDataTexts.shared.title))
    }

    private func retry() {
        state.eventSink(ActivityDataEvent.Retry.shared)
    }
}

/// All data as one inset grouped list under its section chips, which stay at the top: the stats list, the avg/max table, each table in
/// full and the sources, scrolled to `data.start` when it opens. The All data page and a wide activity page's side pane both draw it;
/// the pane puts its own `header` (its title and close) over the chips.
struct ActivityDataList<Before: View, Header: View>: View {
    let data: ActivityData
    let openGear: (String) -> Void
    @ViewBuilder let before: () -> Before
    @ViewBuilder let header: () -> Header

    @Environment(\.stats) private var stats
    /// The sections with a row on screen, by key, so the chip of the first one shows as selected.
    @State private var visible: Set<String> = []
    /// Whether it has scrolled to `data.start` yet.
    @State private var started = false

    init(
        data: ActivityData, openGear: @escaping (String) -> Void, @ViewBuilder before: @escaping () -> Before,
        @ViewBuilder header: @escaping () -> Header = { EmptyView() }
    ) {
        self.data = data
        self.openGear = openGear
        self.before = before
        self.header = header
    }

    var body: some View {
        let options = data.sections.elements(as: DataSectionOption.self)
        let selected = options.firstIndex { visible.contains(Self.key($0.section)) }
        ScrollViewReader { proxy in
            List {
                before()
                // Each section's key as its identity, which a list scrolls to (an id on a row inside a section it doesn't).
                ForEach(options, id: \.key) { option in
                    section(option)
                }
            }
            .listStyle(.insetGrouped)
            .safeAreaInset(edge: .top, spacing: 0) {
                VStack(spacing: 0) {
                    header()
                    SectionChips(labels: options.map(\.label), selected: selected) { index in
                        withAnimation { proxy.scrollTo(Self.key(options[index].section), anchor: .top) }
                    }
                }
                .background(.bar)
            }
            // Opened at a section: scrolled there once the list has laid its rows out, tried again until the section's row is on
            // screen, since a list scrolls only to a row it has placed.
            .task(id: data.start) {
                guard let start = data.start else { return }
                for _ in 0..<Self.startTries where !started {
                    proxy.scrollTo(Self.key(start), anchor: .top)
                    try? await Task.sleep(for: .milliseconds(50))
                }
            }
            .onChange(of: visible) { _, seen in
                if let start = data.start, seen.contains(Self.key(start)) { started = true }
            }
        }
    }

    /// How many times it tries to scroll to `data.start`, a twentieth of a second apart: five seconds for a slow first layout.
    private static var startTries: Int { 100 }

    static func key(_ section: DataSection) -> String { "data/\(section.description)" }

    private func anchor(_ section: DataSection) -> PageAnchor {
        let key = Self.key(section)
        return PageAnchor(key: key) { seen in
            if seen { visible.insert(key) } else { visible.remove(key) }
        }
    }

    @ViewBuilder private func section(_ option: DataSectionOption) -> some View {
        let texts = ActivityDataTexts.shared
        switch option.section {
        case .Stats:
            StatsSections(groups: data.stats.elements(as: StatsGroup.self), anchor: anchor(.Stats), openGear: openGear)
        case .AvgMax:
            AvgMaxSection(rows: data.avgMax.elements(as: AvgMaxRow.self), anchor: anchor(.AvgMax))
        case .Sources:
            Section {
                ForEach(Array(data.sources.elements(as: ChartSource.self).enumerated()), id: \.offset) { index, source in
                    AppListRow(Text(texts.sourceLabel(source: source)), summary: texts.sourceValue(source: source).map { Text($0) })
                        .anchored(index == 0 ? anchor(.Sources) : nil)
                }
            } header: {
                Text(option.label)
            }
        default:
            // A table, in full: the laps, legs, lengths or sets.
            if let charts = data.charts,
                let detail = DetailSection.allCases.first(where: { DataSection.Companion.shared.of(section: $0) == option.section }),
                let table = StructureTableTexts.shared.of(section: detail, charts: charts, units: stats.units)
            {
                Section {
                    LapTableView(table: table).anchored(anchor(option.section))
                } header: {
                    Text(option.label)
                }
            }
        }
    }
}

extension DataSectionOption {
    /// Its key in the All data list, which its chip and an opening at it scroll to.
    var key: String { ActivityDataList<EmptyView, EmptyView>.key(section) }
}

/// A row a page's chips scroll to: its key as the row's id, and whether it is on screen, for the chip that shows as selected.
struct PageAnchor {
    let key: String
    let seen: (Bool) -> Void
}

extension View {
    /// Marks the row `anchor` names, so a chip can scroll to it and follow it; nothing for none. Apply it before a row's list modifiers
    /// (`listRowInsets`, `listRowBackground`): the list doesn't read those through the id.
    @ViewBuilder func anchored(_ anchor: PageAnchor?) -> some View {
        if let anchor {
            // A list that drops a row far off screen may not say it scrolled away, so leaving counts too.
            id(anchor.key)
                .onScrollVisibilityChange(threshold: 0.01) { anchor.seen($0) }
                .onDisappear { anchor.seen(false) }
        } else {
            self
        }
    }
}

/// A row of capsule chips naming a page's sections, `selected` tinted, scrolling sideways when they don't fit and keeping the selected
/// one in view.
struct SectionChips: View {
    let labels: [any AppText]
    let selected: Int?
    let select: (Int) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                        AppChip(filled: index == selected) {
                            select(index)
                        } label: {
                            Text(label)
                        }
                        .accessibilityAddTraits(index == selected ? .isSelected : [])
                        .id(index)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .onChange(of: selected) { _, index in
                guard let index else { return }
                withAnimation { proxy.scrollTo(index, anchor: .center) }
            }
        }
    }
}

#if DEBUG
    #Preview("All data") {
        SamplePages(Samples.activityData) { state in NavigationStack { ActivityDataView(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("All data, at the laps") {
        NavigationStack { ActivityDataView(state: Samples.activityDataAtLaps) }.environment(\.clock, Samples.clock)
    }
#endif
