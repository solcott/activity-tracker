import SwiftUI

/// Trails & roads (segments/segment-browse), drawn from the states `SegmentBrowsePresenter` publishes: the search box, This area and
/// Everywhere while a name is searched over the map's area, the sport choice, the trails, roads and segments on the native map
/// (`SegmentsMapView`), and the list: the area's rows nearest first (a trail or road, or a segment group on none), or the name results
/// page by page, or why there is nothing to list yet. On a phone the map is over
/// the list; on a wider window the list is in a column beside the map, as the shared page lays them out. A run tapped on the map is marked
/// in the list and scrolled to. What it lists and says is Kotlin's (`SegmentBrowseTexts`, `MapBrowseState.listShows`), shared with the
/// Compose page. It has no bar of its own: the Search tab shows it in Trails & roads mode, under its mode choice.
struct SegmentBrowseView: View {
    let state: SegmentBrowseState
    /// Where the map was left, kept by the Search page so switching modes and back reopens it there.
    let memory: SegmentsMapMemory
    @State private var query: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: SegmentBrowseState, memory: SegmentsMapMemory) {
        self.state = state
        self.memory = memory
        _query = State(initialValue: TextFieldModel(state.browse.query))
    }

    var body: some View {
        MapBrowseLayout {
            MapBrowseHeader(browse: state.browse, query: query, searchLabel: SegmentBrowseTexts.shared.searchLabel) {
                AppMenuChip(
                    nil, filled: state.sport != nil,
                    selection: Binding(
                        get: { state.sport ?? "" }, set: { send(SegmentBrowseEvent.SportChanged(sport: $0.isEmpty ? nil : $0)) }),
                    options: [""] + state.sports,
                    optionLabel: { sport in AppStrings.string(SegmentBrowseTexts.shared.sportChoice(sport: sport.isEmpty ? nil : sport)) })
            }
        } map: {
            MapBrowseMap(browse: state.browse, memory: memory)
        } list: {
            MapBrowseList(browse: state.browse, message: { SegmentBrowseTexts.shared.message(list: $0) }) {
                names
            } area: {
                area
            }
        }
        .answersLocationAsk(state.browse)
    }

    private var area: some View {
        ContentStateView(state.area, errorKind: .LoadAreaSegments, retry: retry) {
            List {
                RefreshStatusView(state: state.area, errorKind: .LoadAreaSegments, retry: retry).listStatusRow()
                if state.areaCapped {
                    EmptyMessage(SegmentBrowseTexts.shared.capped).listStatusRow()
                }
                ForEach(state.areaRows, id: \.row.key.value) { run in
                    row(run.row, details: SegmentBrowseTexts.shared.details(run: run, units: stats.units))
                }
                if state.areaRows.isEmpty {
                    EmptyMessage(SegmentBrowseTexts.shared.noneHere).listStatusRow()
                }
            }
            .listStyle(.plain)
        }
    }

    private var names: some View {
        PagedList(
            state.results, of: (any BrowseRow).self, errorKind: .SearchSegments, id: { $0.key.value }, retry: retry,
            loadMore: { send(SegmentBrowseEvent.LoadMore.shared) }
        ) {
        } row: { result in
            row(result, details: SegmentBrowseTexts.shared.details(row: result, units: stats.units))
        } empty: {
            MapBrowseNoResults(browse: state.browse, text: SegmentBrowseTexts.shared.noResults(state: state))
        }
        .listStyle(.plain)
    }

    /// A row: a trail or road (its Trail page opens), or a segment group on none (its segment opens), with its name and details line. A
    /// tap selects it, showing its line on the map, and a tap on the selected one opens it (Kotlin decides which); the selected one is
    /// marked.
    private func row(_ row: any BrowseRow, details: any AppText) -> some View {
        let selected = state.isSelected(row: row)
        return AppItemRow(
            Text(SegmentBrowseTexts.shared.name(row: row)), lines: [Text(details)],
            opens: { send(state.tapRowEvent(row: row)) }
        ) {
            RatingSwatch(rating: row.rating)
        }
        .mapBrowseRow(selected: selected)
    }

    private func retry() {
        send(SegmentBrowseEvent.Retry.shared)
    }

    private func send(_ event: any SegmentBrowseEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Trails & roads") {
        SamplePages(Samples.segmentBrowse) { state in SegmentBrowseView(state: state, memory: SegmentsMapMemory()) }
    }

    #Preview("Trails & roads on an iPad", traits: .landscapeLeft) {
        if let segmentBrowse = Samples.segmentBrowse["selected"] {
            SegmentBrowseView(state: segmentBrowse, memory: SegmentsMapMemory())
        }
    }
#endif
