import SwiftUI

/// The Trail page (segments/path-page), drawn from the states `PathPresenter` publishes: the path's name in the bar, what it is and its
/// data credit, its line muted on the native map (`SegmentsMapView`) with each segment group's line over it, and the segment groups
/// longest first. On a phone the map is over the list; on a wider window the list is in a column beside it, as the shared page lays them
/// out. The map and the list share a selection as browse's do: a tap on a line selects its row and scrolls to it, a tap on a row selects
/// it, and a tap on the selected one opens its segment (Kotlin decides which). Rows in one of the path's sections fold under a
/// `DisclosureGroup`, expanded until collapsed, with an Expand all or Collapse all button once there are two sections; Kotlin keeps
/// which are collapsed and expands one when its line is selected. What it says is Kotlin's (`PathTexts`). Named so because `PathView`
/// is the lexicon's.
struct PathPage: View {
    let state: PathState
    @State private var memory = SegmentsMapMemory()
    @Environment(\.stats) private var stats

    var body: some View {
        ContentStateView(state.content, as: PathContent.self, errorKind: .LoadPath, retry: retry) { _ in
            MapBrowseLayout {
                map
            } list: {
                list
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .pageTitle(state.path.map { Text(verbatim: $0.name) } ?? Text(PathTexts.shared.title))
    }

    private var map: some View {
        SegmentsMapView(
            map: state.segmentsMap, camera: state.camera, memory: memory,
            onViewport: { send(PathEvent.ViewportChanged(viewport: $0)) },
            onSelect: { send(state.selectEvent(uri: $0)) })
    }

    private var list: some View {
        ScrollViewReader { proxy in
            List {
                header
                RefreshStatusView(state: state.content, errorKind: .LoadPath, retry: retry).listStatusRow()
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
            }
            .listStyle(.plain)
            .onChange(of: state.selected?.value) { _, selected in
                guard let selected else { return }
                withAnimation { proxy.scrollTo(selected, anchor: .center) }
            }
        }
    }

    @ViewBuilder private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let path = state.path {
                Text(PathTexts.shared.summary(path: path, units: stats.units)).foregroundStyle(.secondary)
            }
            if let credit = state.credit {
                CreditBox(credit: credit)
            }
            if state.offersAllSports || state.offersAllSections {
                HStack {
                    if state.offersAllSports {
                        AppTextButton(.pathShowAllSports) { send(PathEvent.ShowAllSports.shared) }
                    }
                    if state.offersAllSections {
                        AppTextButton(PathTexts.shared.allSections(allExpanded: state.allSectionsExpanded)) { send(state.allSectionsEvent) }
                    }
                }
            }
        }
        .listStatusRow()
    }

    /// A segment group's row, as browse draws one: its name without the importer's direction, then its details line, marked while
    /// selected.
    private func row(_ group: SegmentGroupRow) -> some View {
        let selected = state.isSelected(group: group)
        return AppItemRow(
            Text(PathTexts.shared.name(group: group)), lines: [Text(PathTexts.shared.details(group: group, units: stats.units))],
            opens: { send(state.tapRowEvent(group: group)) }
        ) {
            EmptyView()
        }
        .listRowBackground(selected ? Color(.secondarySystemFill) : nil)
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

#if DEBUG
    #Preview("Trail") {
        SamplePages(Samples.path) { state in PathPage(state: state) }
    }

    #Preview("Trail on an iPad", traits: .landscapeLeft) {
        PathPage(state: Samples.path["selected"]!)
    }
#endif
