import SwiftUI

/// The Trail page (segments/path-page), drawn from the states `PathPresenter` publishes: the path's name in the bar, what it is and its
/// data credit, its Trail details folded to one line that unfolds their cards (`TrailDetailsSection`), its line muted on the native map
/// (`SegmentsMapView`) with each segment group's line over it, and the segment groups longest first. On a phone the map fills the page
/// under the list's sheet (`MapSheetLayout`), as Apple Maps'; on a wider window the list is in a column beside it, as the shared page lays
/// them out. The map and the list share a selection as browse's do: a tap on a line selects its row and scrolls to it, a tap on a row
/// selects it, and a tap on the selected one opens its segment (Kotlin decides which). Rows in one of the path's sections fold under a
/// `DisclosureGroup`, expanded until collapsed, with an Expand all or Collapse all button once there are two sections; Kotlin keeps which
/// are collapsed and expands one when its line is selected. The search (segments/path-filters) is the navigation bar's, and the sport
/// chips, filters and sort are one toolbar menu, its icon filled while a filter is on; which rows they leave, and what each offers, is
/// Kotlin's (`PathFilter`). A trail with a terrain profile (segments/path-profile) draws it at the top of the list (under the map beside
/// the list when wide) with its rows' stretches marked and the selected one shaded (`PathProfile`), and scrubbing it marks the spot on the
/// map. What it says is Kotlin's (`PathTexts`). Named so because `PathView` is the lexicon's.
struct PathPage: View {
    let state: PathState
    @State private var memory = SegmentsMapMemory()
    /// Where the profile's readout is along its chart, which the map marks (segments/path-profile); nil while it isn't showing.
    @State private var scrubbed: Double?
    /// Whether the list is beside the map (`MapBrowseLayout`'s rule), where the profile docks under the map; on a phone it is the list's
    /// first row, so everything under the map scrolls together.
    @State private var wide = false
    /// Where the list's sheet rests over the map on a phone.
    @State private var detent = MapSheetDetent.middle
    /// The query as typed, held here so the field follows each keystroke; it edits the state's field and follows it back.
    @State private var query: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: PathState) {
        self.state = state
        _query = State(initialValue: TextFieldModel(state.query))
    }

    var body: some View {
        ContentStateView(state.content, as: PathContent.self, errorKind: .LoadPath, retry: retry) { _ in
            if wide {
                MapBrowseLayout {
                    map()
                } list: {
                    list
                }
            } else {
                MapSheetLayout(detent: $detent) { top, bottom in
                    map(coveredTop: top, coveredBottom: bottom)
                } sheet: {
                    list
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onGeometryChange(for: Bool.self) {
            $0.size.width >= SegmentBrowseLayout.sideBySide
        } action: {
            wide = $0
        }
        .pageTitle(state.path.map { Text(verbatim: $0.name) } ?? Text(PathTexts.shared.title))
        .modifier(PathSearch(searchable: state.searchable, query: query, field: state.query, prompt: Text(PathTexts.shared.search)))
        .toolbar {
            if state.searchable || !state.sports.isEmpty {
                ToolbarItem(placement: .primaryAction) { filterMenu }
            }
        }
    }

    /// The sport, difficulty, direction, length and sort choices in one menu, each part shown only while Kotlin offers it.
    private var filterMenu: some View {
        Menu {
            let sports = state.sports
            if !sports.isEmpty {
                Picker(selection: Binding(get: { state.sport ?? "" }, set: { send(state.sportEvent(sport: $0.isEmpty ? nil : $0)) })) {
                    Text(PathTexts.shared.sportChoice(sport: nil)).tag("")
                    ForEach(sports, id: \.self) { sport in
                        Text(PathTexts.shared.sportChoice(sport: sport)).tag(sport)
                    }
                } label: {
                    EmptyView()
                }
                .pickerStyle(.inline)
            }
            let levels = state.levels
            if !levels.isEmpty {
                Section(header: Text(PathTexts.shared.difficulty)) {
                    ForEach(Array(levels.enumerated()), id: \.offset) { _, level in
                        Toggle(isOn: Binding(get: { state.isChosen(level: level) }, set: { _ in send(state.levelEvent(level: level)) })) {
                            Text(PathTexts.shared.level(option: level))
                        }
                    }
                }
            }
            let ways = state.ways
            if !ways.isEmpty {
                Section(header: Text(PathTexts.shared.direction)) {
                    ForEach(Array(ways.enumerated()), id: \.offset) { _, way in
                        Toggle(isOn: Binding(get: { state.isChosen(way: way) }, set: { _ in send(state.wayEvent(way: way)) })) {
                            Text(PathTexts.shared.way(choice: way))
                        }
                    }
                }
            }
            let lengths = state.lengths
            if !lengths.isEmpty {
                Section(header: Text(PathTexts.shared.length)) {
                    ForEach(Array(lengths.enumerated()), id: \.offset) { _, length in
                        Toggle(
                            isOn: Binding(get: { state.isChosen(length: length) }, set: { _ in send(state.lengthEvent(length: length)) })
                        ) {
                            Text(state.lengthText(option: length))
                        }
                    }
                }
            }
            if state.searchable {
                let sorts = state.sorts
                Picker(
                    selection: Binding(
                        get: { sorts.firstIndex { state.isChosen(sort: $0) } ?? 0 },
                        set: { send(state.sortEvent(sort: sorts[$0])) })
                ) {
                    ForEach(Array(sorts.enumerated()), id: \.offset) { index, sort in
                        Text(PathTexts.shared.sort(option: sort)).tag(index)
                    }
                } label: {
                    Label {
                        Text(PathTexts.shared.sort)
                    } icon: {
                        Image(AppIcon.sort)
                    }
                }
                .pickerStyle(.menu)
            }
        } label: {
            Label {
                Text(PathTexts.shared.filters)
            } icon: {
                Image(systemName: state.filtersActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
            }
        }
    }

    /// The map, with the trail's elevation profile under it when wide (segments/path-profile); on a phone the profile is the list's first row,
    /// so everything under the map scrolls together, as the shared page lays it out.
    private func map(coveredTop: CGFloat = 0, coveredBottom: CGFloat = 0) -> some View {
        VStack(spacing: 0) {
            SegmentsMapView(
                map: state.segmentsMap, camera: state.camera, memory: memory,
                onViewport: { send(PathEvent.ViewportChanged(viewport: $0)) },
                onSelect: { send(state.selectEvent(uri: $0)) },
                marker: state.profilePoint(x: scrubbed), coveredTop: coveredTop,
                coveredBottom: coveredBottom)
            if wide {
                profile
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(.systemBackground))
            }
        }
    }

    /// The trail's elevation profile under its title, nothing without one.
    @ViewBuilder private var profile: some View {
        if let chart = state.profileChart {
            VStack(alignment: .leading, spacing: 10) {
                Text(state.profileTitle).font(.subheadline.weight(.semibold))
                LineChartView(chart: chart, height: 110, onScrub: { scrubbed = $0 })
            }
        }
    }

    private var list: some View {
        ScrollViewReader { proxy in
            List {
                if !wide {
                    profile.listRowSeparator(.hidden)
                }
                header
                RefreshStatusView(state: state.content, errorKind: .LoadPath, retry: retry).listStatusRow()
                if state.offersDetails {
                    detailsFold
                    if state.detailsExpanded {
                        TrailDetailsSection(groups: state.trailGroups(), headingsAsRows: true)
                    }
                }
                ForEach(state.items.map(Entry.init)) { entry in
                    if let section = entry.item as? PathListItem.Section {
                        sectionRow(section)
                    } else if let item = entry.item as? PathListItem.Group {
                        row(item.group).id(item.group.shown.uri.value)
                    }
                }
                if state.empty {
                    EmptyMessage(PathTexts.shared.noneHere).listStatusRow()
                }
                if state.noMatches {
                    VStack(spacing: 8) {
                        EmptyMessage(PathTexts.shared.noMatches)
                        AppTextButton(PathTexts.shared.clearFilters) { send(PathEvent.ClearFilters.shared) }
                    }
                    .frame(maxWidth: .infinity)
                    .listStatusRow()
                }
            }
            .listStyle(.plain)
            .onChange(of: state.selected?.value) { _, selected in
                guard let selected else { return }
                withAnimation {
                    // A line tapped on the map raises the sheet from its lowest, so its row shows.
                    if detent == .peek { detent = .middle }
                    proxy.scrollTo(selected, anchor: .center)
                }
            }
        }
    }

    @ViewBuilder private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let summary = state.summary(units: stats.units) {
                Text(summary).foregroundStyle(.secondary)
            }
            if let credit = state.credit {
                CreditBox(credit: credit)
            }
            if state.offersAllSections {
                AppTextButton(PathTexts.shared.allSections(allExpanded: state.allSectionsExpanded)) { send(state.allSectionsEvent) }
            }
        }
        .listStatusRow()
    }

    /// The Trail details folded to one line (segments/trail-attributes): up to three values and the control that unfolds the cards under
    /// it, and folds them again.
    private var detailsFold: some View {
        let expanded = state.detailsExpanded
        return Button {
            send(PathEvent.ToggleDetails.shared)
        } label: {
            HStack(spacing: 8) {
                if let summary = state.detailsSummary() {
                    Text(summary).frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    Spacer()
                }
                Text(TrailTexts.shared.title).foregroundStyle(.tint)
                Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.footnote.weight(.semibold)).foregroundStyle(.tint)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(Text(TrailTexts.shared.toggleLabel(expanded: expanded)))
    }

    /// A segment group's row, as browse draws one: its name without the importer's direction, then its details line, marked while
    /// selected.
    private func row(_ group: SegmentGroupRow) -> some View {
        let selected = state.isSelected(group: group)
        return AppItemRow(
            Text(PathTexts.shared.name(group: group)), lines: [Text(PathTexts.shared.details(group: group, units: stats.units))],
            opens: { send(state.tapRowEvent(group: group)) }
        ) {
            RatingSwatch(rating: group.rating)
        }
        // Clear unless marked, so the list's ground shows: the page's beside the map, the sheet's over it.
        .listRowBackground(selected ? Color(.secondarySystemFill) : Color.clear)
        // The marked row's shading stands alone, with no separator lines over or under it.
        .listRowSeparator(selected ? .hidden : .automatic)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityHint(Text(MapBrowseTexts.shared.rowAction(selected: selected, touch: true)))
    }

    /// A section (segments/path-page): its name and what it covers, its rows under it while expanded.
    private func sectionRow(_ section: PathListItem.Section) -> some View {
        let expanded = state.isExpanded(section: section)
        return DisclosureGroup(
            isExpanded: Binding(get: { expanded }, set: { open in if open != expanded { send(state.toggleEvent(section: section)) } })
        ) {
            ForEach(section.rows.elements(as: SegmentGroupRow.self), id: \.shown.uri.value) { group in
                row(group).id(group.shown.uri.value)
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(PathTexts.shared.name(section: section)).font(.headline)
                Text(PathTexts.shared.details(section: section, units: stats.units)).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .id(section.key)
        .listRowBackground(Color.clear)
        .accessibilityHint(Text(PathTexts.shared.toggleLabel(section: section, expanded: expanded)))
    }

    /// One of the list's items, identified by its key for `ForEach`.
    private struct Entry: Identifiable {
        let item: any PathListItem
        var id: String { item.key }
    }

    private func retry() {
        send(PathEvent.Retry.shared)
    }

    private func send(_ event: any PathEvent) {
        state.eventSink(event)
    }
}

/// The navigation bar's search field over the state's query, only while Kotlin offers it.
private struct PathSearch: ViewModifier {
    let searchable: Bool
    let query: TextFieldModel
    let field: KotlinTextFieldState
    let prompt: Text

    func body(content: Content) -> some View {
        if searchable {
            content
                .searchable(text: Binding(get: { query.text }, set: { query.edit($0) }), prompt: prompt)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .task(id: ObjectIdentifier(field)) {
                    query.bind(to: field)
                    await query.follow(field.values.asAsyncSequence())
                }
        } else {
            content
        }
    }
}

#if DEBUG
    #Preview("Trail") {
        SamplePages(Samples.path) { state in PathPage(state: state) }
    }

    #Preview("Trail on an iPad", traits: .landscapeLeft) {
        if let path = Samples.path["selected"] {
            PathPage(state: path)
        }
    }
#endif
