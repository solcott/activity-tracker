import MapKit
import SwiftUI

/// Many segments on the system's map, panned and zoomed north-up (segments/segment-browse, ui/ios-design-system): runs in the accent colour
/// on a white casing, lifts thin, dashed and muted, and the selected run highlighted over them, in Kotlin's drawing order (`SegmentsMap`,
/// which the shared map's stand-in asks too). A tap selects the run Kotlin finds nearest within a finger's width (`SegmentsMap.nearest`),
/// or clears the choice; each time the map stops it reports what it shows; each new camera request moves it once.
///
/// Where the map was left and which request it took last are kept in `memory`, which the page that holds the map keeps, so a map made
/// again, coming back to Segments mode, opens where it was left rather than where it was first sent, as the shared map's saved camera does.
/// Outside a signed-in session (`\.liveMaps`) it draws the lines on a plain tile instead, so previews and snapshots fetch nothing.
struct SegmentsMapView: View {
    let map: SegmentsMap
    let camera: MapCameraRequest?
    let memory: SegmentsMapMemory
    let onViewport: (MapViewport) -> Void
    let onSelect: (String?) -> Void

    @Environment(\.liveMaps) private var liveMaps

    var body: some View {
        if liveMaps {
            LiveSegmentsMap(map: map, camera: camera, memory: memory, onViewport: onViewport, onSelect: onSelect)
        } else {
            SegmentsSketch(map: map)
        }
    }
}

/// Where a segments map was left, and the last camera request it took, kept by the page beside the map.
@MainActor @Observable
final class SegmentsMapMemory {
    var position: MapCameraPosition?
    var applied: Int32?
}

private struct LiveSegmentsMap: View {
    let map: SegmentsMap
    let camera: MapCameraRequest?
    let memory: SegmentsMapMemory
    let onViewport: (MapViewport) -> Void
    let onSelect: (String?) -> Void

    @State private var position: MapCameraPosition = .automatic
    @State private var size: CGSize = .zero

    var body: some View {
        let lines = map.drawn.elements(as: MapSegment.self)
        MapReader { proxy in
            Map(position: $position, interactionModes: [.pan, .zoom]) {
                ForEach(lines, id: \.uri) { line in
                    let coordinates = line.points.elements(as: GeoPoint.self).map(SegmentsMapGeometry.coordinate)
                    switch line.role {
                    case .Lift:
                        MapPolyline(coordinates: coordinates).stroke(Color(.systemGray), style: SegmentsMapStyle.lift)
                    case .Selected:
                        MapPolyline(coordinates: coordinates).stroke(.white, style: SegmentsMapStyle.selectedCasing)
                        MapPolyline(coordinates: coordinates).stroke(Color("LikeColor"), style: SegmentsMapStyle.selected)
                    default:
                        MapPolyline(coordinates: coordinates).stroke(.white, style: SegmentsMapStyle.casing)
                        // The catalog colour by name: inside a map's content `Color.accentColor` is the system's blue, not the app's tint.
                        MapPolyline(coordinates: coordinates).stroke(Color("AccentColor"), style: SegmentsMapStyle.run)
                    }
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .mapControlVisibility(.hidden)
            .onMapCameraChange(frequency: .onEnd) { context in
                memory.position = .camera(context.camera)
                onViewport(SegmentsMapGeometry.viewport(context.region))
            }
            // Ahead of the map's own gestures: a plain or simultaneous tap gesture on a `Map` never fires (seen on iOS 26), and a tap
            // doesn't compete with its pan and pinch, which still work.
            .highPriorityGesture(SpatialTapGesture().onEnded { tapped in
                let point = tapped.location
                guard let tap = proxy.convert(point, from: .local),
                    let edge = proxy.convert(CGPoint(x: point.x + SegmentsMapStyle.tapSlop, y: point.y), from: .local)
                else { return }
                let reach = SegmentsMapGeometry.meters(from: tap, to: edge)
                onSelect(map.nearest(lat: tap.latitude, lng: tap.longitude, withinMeters: reach))
            })
        }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { newSize in
            size = newSize
            move()
        }
        .onChange(of: camera?.id) { move() }
    }

    /// Takes a camera request the map hasn't taken yet, else goes back to where it was left.
    private func move() {
        guard size.width > 0, size.height > 0 else { return }
        if let camera, camera.id != memory.applied {
            memory.applied = camera.id
            let frame = MapFrame(latitude: camera.lat, longitude: camera.lng, zoom: camera.zoom)
            position = .rect(MapCamera.rect(frame, size: size))
        } else if let left = memory.position, position == .automatic {
            position = left
        }
    }
}

/// The conversions between MapKit's geometry and Kotlin's (a coordinate, a region's box, a tap's reach), kept apart for their tests.
enum SegmentsMapGeometry {
    static func coordinate(_ point: GeoPoint) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
    }

    /// What a map showing `region` reports: its box and centre, as Kotlin rounds them.
    static func viewport(_ region: MKCoordinateRegion) -> MapViewport {
        let centre = region.center
        let halfLat = region.span.latitudeDelta / 2
        let halfLng = region.span.longitudeDelta / 2
        return MapViewport.Companion.shared.ofDegrees(
            south: centre.latitude - halfLat, west: centre.longitude - halfLng, north: centre.latitude + halfLat,
            east: centre.longitude + halfLng, centerLat: centre.latitude, centerLng: centre.longitude)
    }

    /// How far apart two coordinates are on the ground, in metres: how far a tap reaches, from its point to one a finger's width away.
    static func meters(from a: CLLocationCoordinate2D, to b: CLLocationCoordinate2D) -> Double {
        CLLocation(latitude: a.latitude, longitude: a.longitude).distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
    }
}

/// How a segments map draws its lines, as the shared map draws them.
enum SegmentsMapStyle {
    static let run = StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
    static let casing = StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round)
    static let selected = StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round)
    static let selectedCasing = StrokeStyle(lineWidth: 8, lineCap: .round, lineJoin: .round)
    static let lift = StrokeStyle(lineWidth: 2, lineCap: .butt, dash: [6, 4])
    /// A finger's width from a tap, as the shared map's tap box reaches.
    static let tapSlop: CGFloat = 12
}

/// The segments on a plain tile, in Kotlin's drawing order and fit (`SegmentsMap.sketch`): what previews and snapshots show for the map.
private struct SegmentsSketch: View {
    let map: SegmentsMap

    var body: some View {
        Canvas { context, size in
            for line in map.sketch(width: size.width, height: size.height, pad: 16) {
                let points = line.points.elements(as: ShapePoint.self).map { CGPoint(x: $0.x, y: $0.y) }
                guard let first = points.first else { continue }
                var path = Path()
                path.move(to: first)
                for point in points.dropFirst() { path.addLine(to: point) }
                switch line.role {
                case .Lift: context.stroke(path, with: .color(Color(.systemGray)), style: SegmentsMapStyle.lift)
                case .Selected: context.stroke(path, with: .color(Color("LikeColor")), style: SegmentsMapStyle.selected)
                default: context.stroke(path, with: .color(.accentColor), style: SegmentsMapStyle.run)
                }
            }
        }
        .background(Color(.quaternarySystemFill))
        .accessibilityHidden(true)
    }
}
