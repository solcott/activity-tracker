import SwiftUI

/// Dive site browse (dive-sites/site-browse), drawn from the states `DiveSiteBrowsePresenter` publishes: the search box, This area and
/// Everywhere while a name is searched over the map's area, the sites' outlines on the native map (`SegmentsMapView`, which draws each
/// outline as a closed line and selects the one nearest a tap), and the list: the area's sites nearest first, or the name results page by
/// page, or why there is nothing to list yet. It lays out as segment browse does (`SegmentBrowseLayout`). What it lists and says is
/// Kotlin's (`DiveSiteBrowseTexts`, `DiveSiteBrowseState.listShows`), shared with the Compose page. It has no bar of its own: the Search
/// tab shows it in Dive sites mode, under its mode choice.
struct DiveSiteBrowseView: View {
    let state: DiveSiteBrowseState
    /// Where the map was left, kept by the Search page so switching modes and back reopens it there.
    let memory: SegmentsMapMemory
    @State private var query: TextFieldModel
    @State private var width: CGFloat = 0
    @Environment(\.stats) private var stats

    init(state: DiveSiteBrowseState, memory: SegmentsMapMemory) {
        self.state = state
        self.memory = memory
        _query = State(initialValue: TextFieldModel(state.query))
    }

    var body: some View {
        Group {
            if width >= SegmentBrowseLayout.sideBySide {
                HStack(spacing: 0) {
                    VStack(spacing: 0) {
                        header
                        list
                    }
                    .frame(width: SegmentBrowseLayout.listWidth)
                    .frame(maxHeight: .infinity, alignment: .top)
                    Divider()
                    map.frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            } else {
                VStack(spacing: 0) {
                    header
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
        // The shared iOS UI answers the location question at once: the location tracker asks the system itself.
        .task(id: state.askLocation) {
            if state.askLocation { send(DiveSiteBrowseEvent.PermissionResult(granted: true)) }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            AppTextField(
                label: DiveSiteBrowseTexts.shared.searchLabel, model: query, state: state.query,
                // A name is searched as typed: no capitals or corrections put in.
                options: TextFieldOptions(capitalization: .never, autocorrection: false, submitLabel: .search))
            if state.offersScope {
                Picker(selection: Binding(get: { state.scope }, set: { send(DiveSiteBrowseEvent.ScopeChanged(scope: $0)) })) {
                    ForEach(NameScope.allCases, id: \.self) { scope in
                        Text(DiveSiteBrowseTexts.shared.scope(scope: scope)).tag(scope)
                    }
                } label: {
                    Text(DiveSiteBrowseTexts.shared.searchLabel)
                }
                .pickerStyle(.segmented)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    // MARK: Map

    private var map: some View {
        SegmentsMapView(
            map: state.segmentsMap, camera: state.camera, memory: memory,
            onViewport: { send(DiveSiteBrowseEvent.ViewportChanged(viewport: $0)) },
            onSelect: { send(state.selectEvent(uri: $0)) })
    }

    // MARK: List

    @ViewBuilder private var list: some View {
        let shows = state.listShows(mapSupported: true)
        ScrollViewReader { proxy in
            Group {
                switch shows {
                case .Names: names
                case .Area: area
                case .Locating:
                    VStack(spacing: 12) {
                        AppProgress()
                        message(shows)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                default:
                    message(shows).padding(24).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
            }
            .onChange(of: state.selected?.value) { _, selected in
                guard let selected else { return }
                withAnimation { proxy.scrollTo(selected, anchor: .center) }
            }
        }
    }

    @ViewBuilder private func message(_ shows: BrowseList) -> some View {
        if let text = DiveSiteBrowseTexts.shared.message(list: shows) {
            Text(text).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
    }

    private var area: some View {
        ContentStateView(state.area, errorKind: .LoadAreaDiveSites, retry: retry) {
            List {
                RefreshStatusView(state: state.area, errorKind: .LoadAreaDiveSites, retry: retry).listStatusRow()
                if state.areaCapped {
                    note(DiveSiteBrowseTexts.shared.capped)
                }
                ForEach(state.areaSites, id: \.site.uri.value) { browsed in
                    row(browsed.site, details: DiveSiteBrowseTexts.shared.details(browsed: browsed, units: stats.units))
                }
                if state.areaSites.isEmpty {
                    note(DiveSiteBrowseTexts.shared.noneHere)
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
            VStack(spacing: 8) {
                Text(DiveSiteBrowseTexts.shared.noResults(state: state)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                if state.offersSearchEverywhere {
                    AppTextButton(.segmentBrowseSearchEverywhere) { send(DiveSiteBrowseEvent.SearchEverywhere.shared) }
                }
            }
            .frame(maxWidth: .infinity)
        }
        .listStyle(.plain)
    }

    /// A site: its current name, then its details line; the one tapped on the map is marked.
    private func row(_ site: DiveSiteView, details: any AppText) -> some View {
        AppItemRow(Text(verbatim: site.currentName), lines: [Text(details)], opens: { send(DiveSiteBrowseEvent.Open(uri: site.uri)) }) {
            EmptyView()
        }
        .listRowBackground(state.isSelected(site: site) ? Color(.secondarySystemFill) : nil)
        .accessibilityAddTraits(state.isSelected(site: site) ? .isSelected : [])
    }

    private func note(_ text: any AppText) -> some View {
        Text(text).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity).listStatusRow()
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
        DiveSiteBrowseView(state: Samples.diveSiteBrowse["selected"]!, memory: SegmentsMapMemory())
    }
#endif
