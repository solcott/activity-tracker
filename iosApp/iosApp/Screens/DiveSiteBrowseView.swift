import SwiftUI

/// Dive site browse (dive-sites/site-browse), drawn from the states `DiveSiteBrowsePresenter` publishes: the search box, This area and
/// Everywhere while a name is searched over the map's area, the sites' outlines on the native map (`SegmentsMapView`, which draws each
/// outline as a closed line and selects the one nearest a tap), and the list: the area's sites nearest first, or the name results page by
/// page, or why there is nothing to list yet. It lays out and behaves as segment browse does (`MapBrowse.swift`). What it lists and says is
/// Kotlin's (`DiveSiteBrowseTexts`, `MapBrowseState.listShows`), shared with the Compose page. It has no bar of its own: the Search
/// tab shows it in Dive sites mode, under its mode choice.
struct DiveSiteBrowseView: View {
    let state: DiveSiteBrowseState
    /// Where the map was left, kept by the Search page so switching modes and back reopens it there.
    let memory: SegmentsMapMemory
    @State private var query: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: DiveSiteBrowseState, memory: SegmentsMapMemory) {
        self.state = state
        self.memory = memory
        _query = State(initialValue: TextFieldModel(state.browse.query))
    }

    var body: some View {
        MapBrowseLayout {
            MapBrowseHeader(browse: state.browse, query: query, searchLabel: DiveSiteBrowseTexts.shared.searchLabel)
        } map: {
            MapBrowseMap(browse: state.browse, memory: memory)
        } list: {
            MapBrowseList(browse: state.browse, message: { DiveSiteBrowseTexts.shared.message(list: $0) }) {
                names
            } area: {
                area
            }
        }
        .answersLocationAsk(state.browse)
    }

    private var area: some View {
        ContentStateView(state.area, errorKind: .LoadAreaDiveSites, retry: retry) {
            List {
                RefreshStatusView(state: state.area, errorKind: .LoadAreaDiveSites, retry: retry).listStatusRow()
                if state.areaCapped {
                    EmptyMessage(DiveSiteBrowseTexts.shared.capped).listStatusRow()
                }
                ForEach(state.areaSites, id: \.site.uri.value) { browsed in
                    row(browsed.site, details: DiveSiteBrowseTexts.shared.details(browsed: browsed, units: stats.units))
                }
                if state.areaSites.isEmpty {
                    EmptyMessage(DiveSiteBrowseTexts.shared.noneHere).listStatusRow()
                }
            }
            .listStyle(.plain)
        }
    }

    private var names: some View {
        PagedList(
            state.results, of: DiveSiteView.self, errorKind: .SearchDiveSites, id: { $0.uri.value }, retry: retry,
            loadMore: { send(DiveSiteBrowseEvent.LoadMore.shared) }
        ) {
        } row: { site in
            row(site, details: DiveSiteBrowseTexts.shared.details(site: site, units: stats.units))
        } empty: {
            MapBrowseNoResults(browse: state.browse, text: DiveSiteBrowseTexts.shared.noResults(state: state))
        }
        .listStyle(.plain)
    }

    /// A site: its current name, then its details line. A tap selects it, showing it on the map, and a tap on the selected one opens it
    /// (Kotlin decides which), as segment browse's rows do; the selected one is marked.
    private func row(_ site: DiveSiteView, details: any AppText) -> some View {
        let selected = state.isSelected(site: site)
        return AppItemRow(Text(verbatim: site.currentName), lines: [Text(details)], opens: { send(state.tapRowEvent(site: site)) }) {
            EmptyView()
        }
        .mapBrowseRow(selected: selected)
    }

    private func retry() {
        send(DiveSiteBrowseEvent.Retry.shared)
    }

    private func send(_ event: any DiveSiteBrowseEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Dive site browse") {
        SamplePages(Samples.diveSiteBrowse) { state in DiveSiteBrowseView(state: state, memory: SegmentsMapMemory()) }
    }

    #Preview("Dive site browse on an iPad", traits: .landscapeLeft) {
        if let diveSiteBrowse = Samples.diveSiteBrowse["selected"] {
            DiveSiteBrowseView(state: diveSiteBrowse, memory: SegmentsMapMemory())
        }
    }
#endif
