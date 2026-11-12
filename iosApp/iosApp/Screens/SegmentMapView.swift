import SwiftUI

/// A segment's enlarged map (segments/segment-map-viewer), drawn from the states `SegmentMapPresenter` publishes: the segment's line on the
/// native route map filling the page, panned and zoomed north-up with Recenter, titled with the segment's name, and its data credit over
/// the map's top-leading corner, with the line framed below it. What the credit says is Kotlin's (`SegmentCreditTexts`), shared with the
/// Compose page and the segment page.
struct SegmentMapView: View {
    let state: SegmentMapState

    /// The credit's height, so the line is framed below it rather than under it.
    @State private var creditHeight: CGFloat = 0

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadSegment, retry: retry) {
            // Read through a cast, since Swift export erases the content's type.
            if let content = state.content.data as? SegmentMapContent {
                loaded(content)
            }
        }
        .pageTitle(Text(verbatim: (state.content.data as? SegmentMapContent)?.name ?? ""))
    }

    private func loaded(_ content: SegmentMapContent) -> some View {
        ZStack(alignment: .topLeading) {
            if let map = content.routeMap {
                RouteMapView(
                    map: map, height: nil, interactive: true,
                    overlayTop: content.credit == nil ? 0 : creditHeight + SegmentMapLayout.margin)
            }
            VStack(spacing: 8) {
                if let credit = content.credit {
                    CreditBox(credit: credit)
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { creditHeight = $0 }
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                RefreshStatusView(state: state.content, errorKind: .LoadSegment, retry: retry)
            }
            .padding(SegmentMapLayout.margin)
        }
        // The map runs under the home indicator and the tab bar, as a full-screen map does.
        .ignoresSafeArea(edges: .bottom)
    }

    private func retry() {
        state.eventSink(SegmentMapEvent.Retry.shared)
    }
}

/// Where the enlarged map keeps its credit: in from the map's corner, narrow enough to leave Recenter clear on a phone.
enum SegmentMapLayout {
    static let margin: CGFloat = 8
    static let creditWidth: CGFloat = 260
}

/// A segment's data credit on a box that reads over any tiles: OpenStreetMap's linking to its copyright page, then the licence.
struct CreditBox: View {
    let credit: SegmentCredit

    var body: some View {
        let links = SegmentCreditTexts.shared.links(credit: credit)
        VStack(alignment: .leading, spacing: 2) {
            ForEach(Array(SegmentCreditTexts.shared.lines(credit: credit).enumerated()), id: \.offset) { index, line in
                if index == 0, links, let url = URL(string: PlaceTexts.shared.OSM_COPYRIGHT_URL) {
                    Link(destination: url) { Text(line) }.font(.footnote)
                } else {
                    Text(line).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .frame(maxWidth: SegmentMapLayout.creditWidth, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
        .fixedSize(horizontal: false, vertical: true)
    }
}

#if DEBUG
    #Preview("Enlarged segment map") {
        SamplePages(Samples.segmentMap) { state in NavigationStack { SegmentMapView(state: state) } }
    }
#endif
