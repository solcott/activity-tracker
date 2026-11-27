import MapKit
import SwiftUI

/// A route on the system's map (ui/ios-design-system), for any native page that shows one: the activity page, a segment's or dive
/// site's page, segment browse, an invite's route preview. What it draws is Kotlin's (`RouteMap`, which the shared map asks too): each
/// piece of the line smoothed, as a thin line in the accent colour with no casing, a dashed connector across each break with its stop and resume
/// markers, the start and end markers where the viewer's map shows them, and a highlighted stretch over the route when the map has one
/// (`RouteMap.highlight`: a new segment's), wider, in the colour the segments map's selected run takes. It frames the whole route north-up as Kotlin frames it
/// (`RouteMap.frame`: the shared margin, never closer than zoom 16), converted to MapKit's camera by `MapCamera`. The route is drawn on
/// the device; only MapKit's tiles are fetched, for the region shown, and the map shows Apple's credit itself.
///
/// It is static by default, so a drag over it scrolls the page. With `expand`, a tap on it or its expand button opens the enlarged map;
/// `interactive` pans and zooms it, north-up, with Recenter. `overlayTop` is the height of anything the page draws over its top edge,
/// which the frame keeps the route below. A nil `height` fills the space the page gives the map, as the enlarged map does.
///
/// Outside a signed-in session (`\.liveMaps`, which `SessionView` sets) it draws the route as `RouteSketch` instead, so previews and
/// snapshots load no tiles and look the same on every run.
struct RouteMapView: View {
    let map: RouteMap
    var height: CGFloat? = 240
    var interactive = false
    var expand: (() -> Void)?
    var overlayTop: CGFloat = 0
    /// A trim's preview (feed/activity-trim): the route is what's cut, dashed and muted, and the highlight what's kept, in the route
    /// colour, with the start and end markers at its ends.
    var cut = false

    @Environment(\.liveMaps) private var liveMaps

    var body: some View {
        Group {
            if liveMaps {
                LiveRouteMap(map: map, interactive: interactive, overlayTop: overlayTop, cut: cut)
            } else {
                RouteSketch { size in map.sketch(width: size.width, height: size.height, pad: RouteMarkerStyle.sketchPad) }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: height == nil ? .infinity : nil)
        .frame(height: height)
        .overlay(alignment: .topTrailing) {
            if let expand, !interactive {
                MapButton(.expand, label: .mapExpand, action: expand)
            }
        }
        .onTapGesture {
            if !interactive { expand?() }
        }
    }
}

extension EnvironmentValues {
    /// Whether route maps draw MapKit's live map: true in a signed-in session, false in previews and snapshots, which draw the line
    /// preview so they fetch nothing and stay the same on every run.
    @Entry var liveMaps = false
}

/// The MapKit map itself, framed from Kotlin's camera whenever its size or route changes, and again on Recenter.
private struct LiveRouteMap: View {
    let map: RouteMap
    let interactive: Bool
    let overlayTop: CGFloat
    let cut: Bool

    @State private var position: MapCameraPosition = .automatic
    @State private var size: CGSize = .zero
    @State private var shown: MapKit.MapCamera?

    var body: some View {
        let lines = map.pieces.elements(as: MapLine.self).map(Self.coordinates)
        let breaks = map.breaks.elements(as: MapBreak.self)
        Map(position: $position, interactionModes: interactive ? [.pan, .zoom] : []) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                // The catalog colour by name: inside a map's content `Color.accentColor` is the system's blue, not the app's tint.
                if cut {
                    MapPolyline(coordinates: line).stroke(RouteMarkerStyle.cutColor, style: RouteMarkerStyle.cutLine)
                } else {
                    MapPolyline(coordinates: line).stroke(Color("AccentColor"), style: RouteMarkerStyle.line)
                }
            }
            if let highlight = map.highlight {
                let stretch = Self.coordinates(highlight)
                if cut {
                    MapPolyline(coordinates: stretch).stroke(Color("AccentColor"), style: RouteMarkerStyle.line)
                } else {
                    MapPolyline(coordinates: stretch).stroke(RouteMarkerStyle.highlightColor, style: RouteMarkerStyle.highlight)
                }
            }
            ForEach(Array(breaks.enumerated()), id: \.offset) { _, gap in
                MapPolyline(coordinates: [Self.coordinate(gap.stop), Self.coordinate(gap.resume)])
                    .stroke(RouteMarkerStyle.breakColor, style: RouteMarkerStyle.connector)
            }
            ForEach(Array(breaks.enumerated()), id: \.offset) { _, gap in
                Annotation("", coordinate: Self.coordinate(gap.stop), anchor: .center) {
                    RouteMarkerDot(fill: RouteMarkerStyle.breakColor, ring: .white)
                }
                .annotationTitles(.hidden)
                Annotation("", coordinate: Self.coordinate(gap.resume), anchor: .center) {
                    RouteMarkerDot(fill: .white, ring: RouteMarkerStyle.breakColor, ringWidth: RouteMarkerStyle.resumeRing)
                }
                .annotationTitles(.hidden)
            }
            if let start = cut ? map.highlight?.points.elements(as: GeoPoint.self).first ?? map.start : map.start {
                Annotation("", coordinate: Self.coordinate(start), anchor: .center) {
                    RouteMarkerDot(fill: RouteMarkerStyle.startColor, ring: .white)
                }
                .annotationTitles(.hidden)
            }
            if let end = cut ? map.highlight?.points.elements(as: GeoPoint.self).last ?? map.end : map.end {
                Annotation("", coordinate: Self.coordinate(end), anchor: .center) {
                    RouteMarkerDot(fill: RouteMarkerStyle.endColor, ring: .white)
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControlVisibility(.hidden)
        .onGeometryChange(for: CGSize.self) {
            $0.size
        } action: { newSize in
            size = newSize
            frame()
        }
        .onChange(of: map.key) { frame() }
        .onMapCameraChange(frequency: .onEnd) { shown = $0.camera }
        .overlay(alignment: .topTrailing) {
            if interactive {
                VStack(spacing: 0) {
                    MapButton(.recenter, label: .mapRecenter) {
                        withAnimation { frame() }
                    }
                    MapZoomButtons(shown: shown, position: $position)
                }
            }
        }
        .accessibilityHidden(!interactive)
    }

    /// Frames the whole route as Kotlin does, in the map's current size.
    private func frame() {
        guard size.width > 0, size.height > 0 else { return }
        let camera = map.frame(width: size.width, height: size.height, top: overlayTop)
        position = .rect(MapCamera.rect(camera, size: size))
    }

    private static func coordinates(_ line: MapLine) -> [CLLocationCoordinate2D] {
        line.points.elements(as: GeoPoint.self).map(coordinate)
    }

    private static func coordinate(_ point: GeoPoint) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
    }
}

/// Kotlin's camera as MapKit's: Kotlin's zoom draws the world `MapFrame.WORLD` points wide at zoom 0 and twice as wide each step, and
/// MapKit's world is `MKMapSize.world` map points wide, so a map `size` points across shows a rect that many map points per point.
enum MapCamera {
    static func rect(_ frame: MapFrame, size: CGSize) -> MKMapRect {
        let centre = MKMapPoint(CLLocationCoordinate2D(latitude: frame.latitude, longitude: frame.longitude))
        let perPoint = MKMapSize.world.width / (MapFrame.Companion.shared.WORLD * pow(2, frame.zoom))
        let width = size.width * perPoint
        let height = size.height * perPoint
        return MKMapRect(x: centre.x - width / 2, y: centre.y - height / 2, width: width, height: height)
    }
}

/// A button over the map, on a disc so it reads over any tiles.
struct MapButton: View {
    let icon: AppIcon
    let label: LocalizedStringResource
    let action: () -> Void

    init(_ icon: AppIcon, label: LocalizedStringResource, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.action = action
    }

    var body: some View {
        AppIconButton(icon, label: label, action: action)
            .background(.regularMaterial, in: Circle())
            .padding(8)
    }
}

/// Zoom in and out without gestures, a level at a time about the centre: for the simulator's panel, a pointer, or anyone who can't pinch.
struct MapZoomButtons: View {
    /// Where the map last stopped; nothing to zoom from before it has.
    let shown: MapKit.MapCamera?
    @Binding var position: MapCameraPosition

    var body: some View {
        MapButton(.zoomIn, label: .mapZoomIn) { zoom(by: 0.5) }
        MapButton(.zoomOut, label: .mapZoomOut) { zoom(by: 2) }
    }

    private func zoom(by factor: Double) {
        guard var camera = shown else { return }
        camera.distance = min(max(camera.distance * factor, 50), 40_000_000)
        withAnimation { position = .camera(camera) }
    }
}

/// A route marker: a dot filled with `fill` and ringed with `ring`, as the line preview draws its markers.
struct RouteMarkerDot: View {
    let fill: Color
    let ring: Color
    var ringWidth: CGFloat = RouteMarkerStyle.ring

    var body: some View {
        Circle()
            .fill(fill)
            .padding(ringWidth)
            .background(Circle().fill(ring))
            .frame(width: RouteMarkerStyle.radius * 2, height: RouteMarkerStyle.radius * 2)
            .accessibilityHidden(true)
    }
}

/// How a route's line and markers look, on the map and in the line preview alike: copied from the shared UI's `RouteMarkers`, so every
/// platform draws the same marks.
enum RouteMarkerStyle {
    static let startColor = Color(red: 0x43 / 255, green: 0xA0 / 255, blue: 0x47 / 255)
    static let endColor = Color(red: 0xE5 / 255, green: 0x39 / 255, blue: 0x35 / 255)
    static let breakColor = Color(red: 1, green: 0xA0 / 255, blue: 0)
    /// A highlighted stretch's colour: the catalog's, by name, since inside a map's content the asset symbols resolve as the system's.
    static let highlightColor = Color(.like)
    static let radius: CGFloat = 7
    static let ring: CGFloat = 2
    static let resumeRing: CGFloat = 3
    static let connectorWidth: CGFloat = 2.5
    static let dash: CGFloat = 8
    static let space: CGFloat = 5
    /// The margin the line preview keeps clear around a route, as the shared preview does.
    static let sketchPad: CGFloat = 12

    static let line = StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
    /// A highlighted stretch, wider than the line, as the shared map draws one.
    static let highlight = StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round)
    static let connector = StrokeStyle(lineWidth: connectorWidth, dash: [dash, space])
    /// A trim's cut parts, as the shared map draws them: short dashes in a muted colour.
    static let cutColor = Color.gray
    static let cutLine = StrokeStyle(lineWidth: 3, lineCap: .round, dash: [3, 6])
}
