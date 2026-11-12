import SwiftUI

/// Segment browse (segments/segment-browse), drawn from the states `SegmentBrowsePresenter` publishes: the search box, This area and
/// Everywhere while a name is searched over the map's area, the sport choice, the segments on the native map (`SegmentsMapView`), and the
/// list: the area's runs nearest first, or the name results page by page, or why there is nothing to list yet. On a phone the map is over
/// the list; on a wider window the list is in a column beside the map, as the shared page lays them out. A run tapped on the map is marked
/// in the list and scrolled to. What it lists and says is Kotlin's (`SegmentBrowseTexts`, `SegmentBrowseState.listShows`), shared with the
/// Compose page. It has no bar of its own: the Search tab shows it in Segments mode, under its mode choice.
struct SegmentBrowseView: View {
    let state: SegmentBrowseState
    /// Where the map was left, kept by the Search page so switching modes and back reopens it there.
    let memory: SegmentsMapMemory
    @State private var query: TextFieldModel
    @State private var width: CGFloat = 0
    @Environment(\.stats) private var stats

    init(state: SegmentBrowseState, memory: SegmentsMapMemory) {
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
            if state.askLocation { send(SegmentBrowseEvent.PermissionResult(granted: true)) }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            AppTextField(label: SegmentBrowseTexts.shared.searchLabel, model: query, state: state.query)
            if state.offersScope {
                Picker(selection: Binding(get: { state.scope }, set: { send(SegmentBrowseEvent.ScopeChanged(scope: $0)) })) {
                    ForEach(NameScope.allCases, id: \.self) { scope in
                        Text(SegmentBrowseTexts.shared.scope(scope: scope)).tag(scope)
                    }
                } label: {
                    Text(SegmentBrowseTexts.shared.searchLabel)
                }
                .pickerStyle(.segmented)
            }
            AppMenuChip(
                nil, filled: state.sport != nil,
                selection: Binding(get: { state.sport ?? "" }, set: { send(SegmentBrowseEvent.SportChanged(sport: $0.isEmpty ? nil : $0)) }),
                options: [""] + state.sports,
                optionLabel: { sport in
                    AppStrings.string(sport.isEmpty ? SegmentBrowseTexts.shared.allSports : SegmentBrowseTexts.shared.sport(sport: sport))
                })
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    // MARK: Map

    private var map: some View {
        SegmentsMapView(
            map: state.segmentsMap, camera: state.camera, memory: memory,
            onViewport: { send(SegmentBrowseEvent.ViewportChanged(viewport: $0)) },
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
        if let text = SegmentBrowseTexts.shared.message(list: shows) {
            Text(text).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
    }

    private var area: some View {
        ContentStateView(state.area, errorKind: .LoadAreaSegments, retry: retry) {
            List {
                RefreshStatusView(state: state.area, errorKind: .LoadAreaSegments, retry: retry).listStatusRow()
                if state.areaCapped {
                    note(SegmentBrowseTexts.shared.capped)
                }
                ForEach(state.areaRuns, id: \.segment.uri.value) { run in
                    row(run.segment, details: SegmentBrowseTexts.shared.details(run: run, units: stats.units))
                }
                if state.areaRuns.isEmpty {
                    note(SegmentBrowseTexts.shared.noneHere)
                }
            }
            .listStyle(.plain)
        }
    }

    private var names: some View {
        PagedList(
            state.results, of: SegmentView.self, errorKind: .SearchSegments, id: { $0.uri.value }, retry: retry,
            loadMore: { send(SegmentBrowseEvent.LoadMore.shared) }
        ) {
        } row: { segment in
            row(segment, details: SegmentBrowseTexts.shared.details(segment: segment, units: stats.units))
        } empty: {
            VStack(spacing: 8) {
                Text(SegmentBrowseTexts.shared.noResults(state: state)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                if state.offersSearchEverywhere {
                    AppTextButton(.segmentBrowseSearchEverywhere) { send(SegmentBrowseEvent.SearchEverywhere.shared) }
                }
            }
            .frame(maxWidth: .infinity)
        }
        .listStyle(.plain)
    }

    /// A segment: its name, then its details line; the one tapped on the map is marked.
    private func row(_ segment: SegmentView, details: any AppText) -> some View {
        AppItemRow(
            Text(verbatim: segment.currentName), lines: [Text(details)], opens: { send(SegmentBrowseEvent.Open(uri: segment.uri)) }
        ) {
            EmptyView()
        }
        .listRowBackground(state.isSelected(segment: segment) ? Color(.secondarySystemFill) : nil)
        .accessibilityAddTraits(state.isSelected(segment: segment) ? .isSelected : [])
    }

    private func note(_ text: any AppText) -> some View {
        Text(text).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity).listStatusRow()
    }

    private func retry() {
        send(SegmentBrowseEvent.Retry.shared)
    }

    private func send(_ event: any SegmentBrowseEvent) {
        state.eventSink(event)
    }
}

/// How segment browse lays out, as the shared page does: side by side from 600 points wide, the list column 380 wide; on a phone the
/// map takes 1.2 shares of the height to the list's 1.
enum SegmentBrowseLayout {
    static let sideBySide: CGFloat = 600
    static let listWidth: CGFloat = 380
    static let mapShare: CGFloat = 1.2 / 2.2
}

#if DEBUG
    #Preview("Segment browse") {
        SamplePages(Samples.segmentBrowse) { state in SegmentBrowseView(state: state, memory: SegmentsMapMemory()) }
    }

    #Preview("Segment browse on an iPad", traits: .landscapeLeft) {
        SegmentBrowseView(state: Samples.segmentBrowse["selected"]!, memory: SegmentsMapMemory())
    }
#endif
