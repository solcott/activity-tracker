import MapKit
import SwiftUI

/// A route on the system's map (ui/ios-design-system), for any native page that shows one: the activity page, a segment's or dive
/// site's page, segment browse, an invite's route preview. What it draws is Kotlin's (`RouteMap`, which the shared map asks too): each
/// piece of the line smoothed, in the accent colour on a white casing, a dashed connector across each break with its stop and resume
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

    @Environment(\.liveMaps) private var liveMaps

    var body: some View {
        Group {
            if liveMaps {
                LiveRouteMap(map: map, interactive: interactive, overlayTop: overlayTop)
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

    @State private var position: MapCameraPosition = .automatic
    @State private var size: CGSize = .zero

    var body: some View {
        let lines = map.pieces.elements(as: MapLine.self).map(Self.coordinates)
        let breaks = map.breaks.elements(as: MapBreak.self)
        Map(position: $position, interactionModes: interactive ? [.pan, .zoom] : []) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                MapPolyline(coordinates: line).stroke(.white, style: RouteMarkerStyle.casing)
            }
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                // The catalog colour by name: inside a map's content `Color.accentColor` is the system's blue, not the app's tint.
                MapPolyline(coordinates: line).stroke(Color("AccentColor"), style: RouteMarkerStyle.line)
            }
            if let highlight = map.highlight {
                let stretch = Self.coordinates(highlight)
                MapPolyline(coordinates: stretch).stroke(.white, style: RouteMarkerStyle.highlightCasing)
                MapPolyline(coordinates: stretch).stroke(RouteMarkerStyle.highlightColor, style: RouteMarkerStyle.highlight)
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
            if let start = map.start {
                Annotation("", coordinate: Self.coordinate(start), anchor: .center) {
                    RouteMarkerDot(fill: RouteMarkerStyle.startColor, ring: .white)
                }
                .annotationTitles(.hidden)
            }
            if let end = map.end {
                Annotation("", coordinate: Self.coordinate(end), anchor: .center) {
                    RouteMarkerDot(fill: RouteMarkerStyle.endColor, ring: .white)
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControlVisibility(.hidden)
        .onGeometryChange(for: CGSize.self) { $0.size } action: { newSize in
            size = newSize
            frame()
        }
        .onChange(of: map.key) { frame() }
        .overlay(alignment: .topTrailing) {
            if interactive {
                MapButton(.recenter, label: .mapRecenter) {
                    withAnimation { frame() }
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
private struct MapButton: View {
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
    static let highlightColor = Color("LikeColor")
    static let radius: CGFloat = 7
    static let ring: CGFloat = 2
    static let resumeRing: CGFloat = 3
    static let connectorWidth: CGFloat = 2.5
    static let dash: CGFloat = 8
    static let space: CGFloat = 5
    /// The margin the line preview keeps clear around a route, as the shared preview does.
    static let sketchPad: CGFloat = 12

    static let line = StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
    static let casing = StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round)
    /// A highlighted stretch, wider than the line, as the shared map draws one.
    static let highlight = StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round)
    static let highlightCasing = StrokeStyle(lineWidth: 9, lineCap: .round, lineJoin: .round)
    static let connector = StrokeStyle(lineWidth: connectorWidth, dash: [dash, space])
}
