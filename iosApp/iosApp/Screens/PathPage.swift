import SwiftUI

/// The Trail page (segments/path-page), drawn from the states `PathPresenter` publishes: the path's name in the bar, what it is and its
/// data credit, its line muted on the native map (`SegmentsMapView`) with each segment group's line over it, and the segment groups
/// longest first. On a phone the map is over the list; on a wider window the list is in a column beside it, as the shared page lays them
/// out. The map and the list share a selection as browse's do: a tap on a line selects its row and scrolls to it, a tap on a row selects
/// it, and a tap on the selected one opens its segment (Kotlin decides which). What it says is Kotlin's (`PathTexts`). Named so because
/// `PathView` is the lexicon's.
struct PathPage: View {
    let state: PathState
    @State private var memory = SegmentsMapMemory()
    @State private var width: CGFloat = 0
    @Environment(\.stats) private var stats

    var body: some View {
        ContentStateView(state.content, as: PathContent.self, errorKind: .LoadPath, retry: retry) { _ in
            Group {
                if width >= SegmentBrowseLayout.sideBySide {
                    HStack(spacing: 0) {
                        list.frame(width: SegmentBrowseLayout.listWidth)
                        Divider()
                        map.frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                } else {
                    GeometryReader { geometry in
                        VStack(spacing: 0) {
                            map.frame(height: geometry.size.height * SegmentBrowseLayout.mapShare)
                            list
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
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
                ForEach(state.groups, id: \.shown.uri.value) { group in
                    row(group).id(group.shown.uri.value)
                }
                if state.empty {
                    Text(PathTexts.shared.noneHere).foregroundStyle(.secondary).frame(maxWidth: .infinity).listStatusRow()
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
            if state.offersAllSports {
                AppTextButton(.pathShowAllSports) { send(PathEvent.ShowAllSports.shared) }
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
