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
        .onGeometryChange(for: CGFloat.self) {
            $0.size.width
        } action: {
            width = $0
        }
    }
}

extension MapBrowseLayout where Header == EmptyView {
    /// A page with no header over its list (the Trail page, whose list starts with its own).
    init(@ViewBuilder map: () -> Map, @ViewBuilder list: () -> ListContent) {
        self.init(header: { EmptyView() }, map: map, list: list)
    }
}

/// Where a phone map page's sheet rests (`MapSheetLayout`): low with only its top showing above the tab bar, over half the screen
/// (where it opens), or up to the bars over the whole map.
enum MapSheetDetent: CaseIterable {
    case peek, middle, full

    /// How tall the sheet stands at this detent, from the screen's bottom, on a screen [height] points tall whose bars take [top] and
    /// [bottom] of it.
    func height(in height: CGFloat, top: CGFloat, bottom: CGFloat) -> CGFloat {
        switch self {
        case .peek: MapSheetStyle.peek + bottom
        case .middle: height * MapSheetStyle.middleShare
        case .full: height - top
        }
    }
}

/// The sheet drawn as the system's own sheet is, measured from a `UISheetPresentationController` sheet at an undimmed detent: the top
/// corners' continuous radius, the edge shadow (iOS 18 draws a thin one, iOS 26 none), and the background a presented sheet resolves
/// `systemBackground` to, the elevated level's (white in light, a lifted grey in dark rather than black).
enum MapSheetStyle {
    static let peek: CGFloat = 96
    /// How much of the screen's height the sheet takes where it opens.
    static let middleShare: CGFloat = 0.5
    /// How far past the list's top a pull goes before the sheet follows it down.
    static let pullSlack: CGFloat = 4
    static var cornerRadius: CGFloat {
        if #available(iOS 26, *) { 38 } else { 10 }
    }
    static var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: cornerRadius, topTrailingRadius: cornerRadius, style: .continuous)
    }
    static let background = Color(
        uiColor: UIColor { traits in
            UIColor.systemBackground.resolvedColor(with: traits.modifyingTraits { $0.userInterfaceLevel = .elevated })
        })
    /// The system sheet's shadow: none on iOS 26; on iOS 18 a 2-point one in black at 10% in light and 24% in dark.
    static let shadow = Color(
        uiColor: UIColor { traits in
            if #available(iOS 26, *) { return .clear }
            return UIColor.black.withAlphaComponent(traits.userInterfaceStyle == .dark ? 0.24 : 0.1)
        })
    static let shadowRadius: CGFloat = 2
}

/// A phone map page laid out as Apple Maps' is: the map filling the page and the list in a sheet over it, which drags between its
/// detents (`MapSheetDetent`) by its grabber or its content. Below the top detent a drag on the list moves the sheet and the list stays
/// put; at the top the list scrolls, and a pull down from the list's top brings the sheet down. The map and the sheet run on under the
/// bars, the list's last rows scrolling up from under the tab bar. [map] is told how much of its top the bars cover and of its bottom the
/// sheet where it opens, to frame what it shows between them.
struct MapSheetLayout<Map: View, Sheet: View>: View {
    @Binding var detent: MapSheetDetent
    @ViewBuilder let map: (_ coveredTop: CGFloat, _ coveredBottom: CGFloat) -> Map
    @ViewBuilder let sheet: Sheet

    /// How far the current drag has moved the sheet up (negative) or down.
    @State private var dragged: CGFloat = 0
    /// Whether this drag moves the sheet, decided when it starts.
    @State private var moving: Bool?
    /// Whether the list is scrolled to its top.
    @State private var listAtTop = true

    var body: some View {
        // The bars' heights from the safe area's frame within the whole screen's, which the search field under the bar is part of.
        GeometryReader { safe in
            GeometryReader { geometry in
                let safeFrame = safe.frame(in: .global)
                let screen = geometry.frame(in: .global)
                let height = geometry.size.height
                let top = max(safeFrame.minY - screen.minY, 0)
                let bottom = max(screen.maxY - safeFrame.maxY, 0)
                let detentHeight = { (detent: MapSheetDetent) in detent.height(in: height, top: top, bottom: bottom) }
                let shown = min(max(detentHeight(detent) - dragged, detentHeight(.peek)), detentHeight(.full))
                ZStack(alignment: .bottom) {
                    map(top, detentHeight(.middle))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    VStack(spacing: 0) {
                        Capsule()
                            .fill(Color(.tertiaryLabel))
                            .frame(width: 36, height: 5)
                            .padding(.vertical, 6)
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                            .accessibilityElement()
                            .accessibilityLabel(Text(.mapSheetHandle))
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction { cycle() }
                        // The sheet's own background shows through a list in it, as in a system sheet.
                        sheet
                            .scrollContentBackground(.hidden)
                            .scrollDisabled(detent != .full || moving == true)
                            .onScrollGeometryChange(for: Bool.self) { scroll in
                                scroll.contentOffset.y <= -scroll.contentInsets.top + MapSheetStyle.pullSlack
                            } action: { _, atTop in
                                listAtTop = atTop
                            }
                    }
                    .frame(height: shown, alignment: .top)
                    .frame(maxWidth: .infinity)
                    // The content clipped to the rounded top, the background left whole to run on under the tab bar.
                    .clipShape(MapSheetStyle.shape)
                    .background {
                        MapSheetStyle.shape
                            .fill(MapSheetStyle.background)
                            .shadow(color: MapSheetStyle.shadow, radius: MapSheetStyle.shadowRadius)
                            .ignoresSafeArea(edges: .bottom)
                    }
                    .simultaneousGesture(drag(detentHeight))
                }
            }
            // The whole screen, so the map shows under the bars and the list scrolls on under the tab bar, which still insets its end.
            .ignoresSafeArea(.container)
        }
    }

    private func drag(_ detentHeight: @escaping (MapSheetDetent) -> CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .global)
            .onChanged { value in
                if moving == nil {
                    let vertical = abs(value.translation.height) > abs(value.translation.width)
                    // At the top detent the list scrolls; only a pull down from its top moves the sheet.
                    moving = vertical && (detent != .full || (listAtTop && value.translation.height > 0))
                }
                if moving == true { dragged = value.translation.height }
            }
            .onEnded { value in
                defer { moving = nil }
                guard moving == true else { return }
                let landing = detentHeight(detent) - value.predictedEndTranslation.height
                let next = MapSheetDetent.allCases.min { abs(detentHeight($0) - landing) < abs(detentHeight($1) - landing) }
                withAnimation(.snappy) {
                    detent = next ?? detent
                    dragged = 0
                }
            }
    }

    /// The grabber's action for VoiceOver: up a detent, and from the top back to the bottom.
    private func cycle() {
        withAnimation(.snappy) {
            detent =
                switch detent {
                case .peek: .middle
                case .middle: .full
                case .full: .peek
                }
        }
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
        listRowBackground(selected ? Color.selectedRow : nil)
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
