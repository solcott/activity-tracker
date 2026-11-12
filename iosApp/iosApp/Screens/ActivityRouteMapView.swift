import SwiftUI

/// An activity's enlarged route map (feed/route-map-viewer), drawn from the states `RouteMapPresenter` publishes: the route on the native
/// map filling the page under the system's bar, panned and zoomed north up with Recenter, as the detail page's map draws it, titled with
/// the activity's title or type (`RouteMapTexts`, which the Compose page asks too). Named so because `RouteMapView` is the map itself.
struct ActivityRouteMapView: View {
    let state: RouteMapState

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadActivity, retry: retry) {
            ZStack(alignment: .top) {
                if let content = state.content.data as? RouteMapContent, let map = RouteMapTexts.shared.routeMap(content: content) {
                    RouteMapView(map: map, height: nil, interactive: true)
                }
                RefreshStatusView(state: state.content, errorKind: .LoadActivity, retry: retry).padding(8)
            }
            // The map runs under the home indicator, as a full-screen map does.
            .ignoresSafeArea(edges: .bottom)
        }
        .pageTitle(Text(RouteMapTexts.shared.title(content: state.content.data as? RouteMapContent)))
    }

    private func retry() {
        state.eventSink(RouteMapEvent.Retry.shared)
    }
}

#if DEBUG
    #Preview("Enlarged route map") {
        SamplePages(Samples.routeMap) { state in NavigationStack { ActivityRouteMapView(state: state) } }
    }
#endif
