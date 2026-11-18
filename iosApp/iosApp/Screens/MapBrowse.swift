import SwiftUI

/// What the pages that list things beside a map share (segment browse, dive site browse and the Trail page), so they lay out and behave
/// alike: the layout, and for the two browse pages, which follow Kotlin's `MapBrowseState`, the header, the map, the list's states and the
/// row marks.

/// How the map pages lay out, as the shared pages do: side by side from 600 points wide, the list column 380 wide; on a phone the map
/// takes 1.2 shares of the height to the list's 1.
enum SegmentBrowseLayout {
    static let sideBySide: CGFloat = 600
    static let listWidth: CGFloat = 380
    static let mapShare: CGFloat = 1.2 / 2.2
}

/// A map page's layout: on a phone the header, then the map over the list; on a wider window the header over the list in a column beside
/// the map.
struct MapBrowseLayout<Header: View, Map: View, ListContent: View>: View {
    @ViewBuilder let header: Header
    @ViewBuilder let map: Map
    @ViewBuilder let list: ListContent
    @State private var width: CGFloat = 0

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
    }
}

extension MapBrowseLayout where Header == EmptyView {
    /// A page with no header over its list (the Trail page, whose list starts with its own).
    init(@ViewBuilder map: () -> Map, @ViewBuilder list: () -> ListContent) {
        self.init(header: { EmptyView() }, map: map, list: list)
    }
}

/// A browse page's header: the name search, This area and Everywhere while a name is searched over the map's area, then the page's own
/// choices (`extra`, segment browse's sport).
struct MapBrowseHeader<Extra: View>: View {
    let browse: MapBrowseState
    let query: TextFieldModel
    let searchLabel: any AppText
    @ViewBuilder let extra: Extra

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AppTextField(
                label: searchLabel, model: query, state: browse.query,
                // A name is searched as typed, as the athlete search's field: no capitals or corrections put in.
                options: TextFieldOptions(capitalization: .never, autocorrection: false, submitLabel: .search))
            if browse.offersScope {
                Picker(selection: Binding(get: { browse.scope }, set: { browse.eventSink(MapBrowseEvent.ScopeChanged(scope: $0)) })) {
                    ForEach(NameScope.allCases, id: \.self) { scope in
                        Text(MapBrowseTexts.shared.scope(scope: scope)).tag(scope)
                    }
                } label: {
                    Text(searchLabel)
                }
                .pickerStyle(.segmented)
            }
            extra
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

extension MapBrowseHeader where Extra == EmptyView {
    init(browse: MapBrowseState, query: TextFieldModel, searchLabel: any AppText) {
        self.init(browse: browse, query: query, searchLabel: searchLabel) { EmptyView() }
    }
}

/// A browse page's map: the lines Kotlin chose on the native map, the viewport and taps sent back to it.
struct MapBrowseMap: View {
    let browse: MapBrowseState
    let memory: SegmentsMapMemory

    var body: some View {
        SegmentsMapView(
            map: browse.segmentsMap, camera: browse.camera, memory: memory,
            onViewport: { browse.eventSink(MapBrowseEvent.ViewportChanged(viewport: $0)) },
            onSelect: { browse.eventSink(browse.selectEvent(uri: $0)) })
    }
}

/// A browse page's list: what Kotlin's `listShows` says it shows (the name results, the area's rows, or why there is nothing to list
/// yet, with a spinner while locating), scrolled to the row a tap on the map selects.
struct MapBrowseList<Names: View, Area: View>: View {
    let browse: MapBrowseState
    /// The page's text for what the list shows (`message(list:)` of its texts), or nil for none.
    let message: (BrowseList) -> (any AppText)?
    @ViewBuilder let names: Names
    @ViewBuilder let area: Area

    var body: some View {
        let shows = browse.listShows(mapSupported: true)
        ScrollViewReader { proxy in
            Group {
                switch shows {
                case .Names: names
                case .Area: area
                case .Locating:
                    VStack(spacing: 12) {
                        AppProgress()
                        messageText(shows)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                default:
                    messageText(shows).padding(24).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
            }
            .onChange(of: browse.selected?.value) { _, selected in
                guard let selected else { return }
                withAnimation { proxy.scrollTo(selected, anchor: .center) }
            }
        }
    }

    @ViewBuilder private func messageText(_ shows: BrowseList) -> some View {
        if let text = message(shows) {
            EmptyMessage(text)
        }
    }
}

/// What a name search that found nothing says, with Search everywhere while it searched only the map's area.
struct MapBrowseNoResults: View {
    let browse: MapBrowseState
    let text: any AppText

    var body: some View {
        VStack(spacing: 8) {
            EmptyMessage(text)
            if browse.offersSearchEverywhere {
                AppTextButton(.segmentBrowseSearchEverywhere) { browse.eventSink(MapBrowseEvent.SearchEverywhere.shared) }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

extension View {
    /// Marks a browse row as the selected one, which a tap opens and any other tap selects (Kotlin decides which).
    func mapBrowseRow(selected: Bool) -> some View {
        listRowBackground(selected ? Color(.secondarySystemFill) : nil)
            .accessibilityAddTraits(selected ? .isSelected : [])
            .accessibilityHint(Text(MapBrowseTexts.shared.rowAction(selected: selected, touch: true)))
    }

    /// Answers a browse page's location question at once: in the iOS app the location tracker asks the system itself.
    func answersLocationAsk(_ browse: MapBrowseState) -> some View {
        task(id: browse.askLocation) {
            if browse.askLocation { browse.eventSink(MapBrowseEvent.PermissionResult(granted: true)) }
        }
    }
}
