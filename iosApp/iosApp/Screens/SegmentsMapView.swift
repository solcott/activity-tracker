import MapKit
import SwiftUI

/// Many segments on the system's map, panned and zoomed north-up (segments/segment-browse, ui/ios-design-system): runs in their rating
/// level's colour on its casing with the levels' legend in the top-leading corner (segments/trail-ratings, `TrailRatingStyle`), lifts thin, dashed and muted, and the selected run highlighted over them, in Kotlin's drawing order (`SegmentsMap`,
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
    /// A place the map marks without moving, such as the spot a trail profile's readout marks (segments/path-profile).
    var marker: GeoPoint?
    /// The lines riders say are closed (segments/trail-reports), each labelled Closed at its middle, in words as well as colour.
    var closed: Set<String> = []
    /// How much of the map's top and bottom something lies over (the bars, the Trail page's sheet), so a camera request frames its
    /// place in the rest.
    var coveredTop: CGFloat = 0
    var coveredBottom: CGFloat = 0

    @Environment(\.liveMaps) private var liveMaps

    var body: some View {
        Group {
            if liveMaps {
                LiveSegmentsMap(
                    map: map, camera: camera, memory: memory, onViewport: onViewport, onSelect: onSelect, marker: marker,
                    closed: closed, coveredTop: coveredTop, coveredBottom: coveredBottom)
            } else {
                SegmentsSketch(map: map, closed: closed, coveredTop: coveredTop, coveredBottom: coveredBottom)
            }
        }
        .overlay(alignment: .topLeading) { RatingLegendView(map: map).padding(8).padding(.top, coveredTop) }
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
    let marker: GeoPoint?
    let closed: Set<String>
    let coveredTop: CGFloat
    let coveredBottom: CGFloat

    @State private var position: MapCameraPosition = .automatic
    @State private var size: CGSize = .zero
    @State private var shown: MapKit.MapCamera?

    var body: some View {
        let lines = map.drawn.elements(as: MapSegment.self)
        MapReader { proxy in
            Map(position: $position, interactionModes: [.pan, .zoom]) {
                // By URI and role: MapKit keeps an overlay whose identity is unchanged as it was drawn, so a run selected or hovered
                // is made anew in its highlight, on top in Kotlin's drawing order.
                ForEach(lines, id: \.mapIdentity) { line in
                    let coordinates = line.points.elements(as: GeoPoint.self).map(SegmentsMapGeometry.coordinate)
                    switch line.role {
                    case .Lift:
                        MapPolyline(coordinates: coordinates).stroke(Color(.systemGray), style: SegmentsMapStyle.lift)
                    case .Selected:
                        MapPolyline(coordinates: coordinates).stroke(Color("LikeColor"), style: SegmentsMapStyle.selected)
                    default:
                        let rating = line.rating
                        MapPolyline(coordinates: coordinates).stroke(TrailRatingStyle.casing(rating), style: TrailRatingStyle.casingStroke)
                        MapPolyline(coordinates: coordinates).stroke(
                            TrailRatingStyle.line(rating, trail: line.trail), style: TrailRatingStyle.lineStroke)
                        if TrailRatingStyle.isDouble(rating) {
                            MapPolyline(coordinates: coordinates).stroke(.white, style: TrailRatingStyle.centre)
                        }
                    }
                }
                ForEach(lines.filter { closed.contains($0.uri) }, id: \.uri) { line in
                    if let middle = SegmentsMapGeometry.middle(of: line) {
                        Annotation("", coordinate: middle, anchor: .bottom) { ClosedBadge() }
                            .annotationTitles(.hidden)
                    }
                }
                if let marker {
                    Annotation("", coordinate: SegmentsMapGeometry.coordinate(marker), anchor: .center) {
                        Circle().fill(Color("LikeColor"))
                            .overlay(Circle().strokeBorder(Color(.systemBackground), lineWidth: 2))
                            .frame(width: 14, height: 14)
                    }
                    .annotationTitles(.hidden)
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .mapControlVisibility(.hidden)
            .onMapCameraChange(frequency: .onEnd) { context in
                shown = context.camera
                memory.position = .camera(context.camera)
                onViewport(SegmentsMapGeometry.viewport(context.region))
            }
            // Ahead of the map's own gestures: a plain or simultaneous tap gesture on a `Map` never fires (seen on iOS 26), and a tap
            // doesn't compete with its pan and pinch, which still work.
            .highPriorityGesture(
                SpatialTapGesture().onEnded { tapped in
                    let point = tapped.location
                    guard let tap = proxy.convert(point, from: .local),
                        let edge = proxy.convert(CGPoint(x: point.x + SegmentsMapStyle.tapSlop, y: point.y), from: .local)
                    else { return }
                    let reach = SegmentsMapGeometry.meters(from: tap, to: edge)
                    onSelect(map.nearest(lat: tap.latitude, lng: tap.longitude, withinMeters: reach))
                })
        }
        .overlay(alignment: .topTrailing) {
            VStack(spacing: 0) { MapZoomButtons(shown: shown, position: $position) }.padding(.top, coveredTop)
        }
        .onGeometryChange(for: CGSize.self) {
            $0.size
        } action: { newSize in
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
            // A selected row's line is framed at the map's size, less what lies over its top and bottom; a place to open at keeps its
            // zoom.
            let open = CGSize(width: size.width, height: max(size.height - coveredTop - coveredBottom, size.height / 4))
            let frame = camera.frame(width: Double(open.width), height: Double(open.height), bottom: 0)
            var rect = MapCamera.rect(frame, size: open)
            // The same scale on over the covered parts, so the framed place sits in the open part between them.
            let perPoint = rect.size.height / open.height
            rect.origin.y -= coveredTop * perPoint
            rect.size.height = size.height * perPoint
            position = .rect(rect)
        } else if let left = memory.position, position == .automatic {
            position = left
        }
    }
}

extension MapSegment {
    /// A line's identity on the map: its URI and how it's drawn, so a change of role or level replaces its overlay.
    fileprivate var mapIdentity: String { "\(uri)|\(role)|\(String(describing: rating))|\(trail)" }
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

    /// Where a line's Closed label stands: halfway along its positions, or nil for a line with none.
    static func middle(of line: MapSegment) -> CLLocationCoordinate2D? {
        let points = line.points.elements(as: GeoPoint.self).map(coordinate)
        return middle(points.map { CGPoint(x: $0.longitude, y: $0.latitude) }).map {
            CLLocationCoordinate2D(latitude: $0.y, longitude: $0.x)
        }
    }

    /// The point halfway through `points` by index: the middle one, or between the two middle ones.
    static func middle(_ points: [CGPoint]) -> CGPoint? {
        guard !points.isEmpty else { return nil }
        let a = points[(points.count - 1) / 2]
        let b = points[points.count / 2]
        return CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
    }

    /// Where a line drawn on a stand-in tile takes its label: the point along it nearest its middle whose height is in `open` (the tile
    /// less what lies over it), else its middle moved into `open`, so the label shows even when the line is under the Trail page's sheet.
    static func labelSpot(_ points: [CGPoint], open: ClosedRange<CGFloat>) -> CGPoint? {
        guard let middle = middle(points) else { return nil }
        if open.contains(middle.y) { return middle }
        var samples: [CGPoint] = []
        for (a, b) in zip(points, points.dropFirst()) {
            for step in 0...20 {
                let t = CGFloat(step) / 20
                samples.append(CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t))
            }
        }
        let visible = samples.filter { open.contains($0.y) }
        if let nearest = visible.min(by: { hypot($0.x - middle.x, $0.y - middle.y) < hypot($1.x - middle.x, $1.y - middle.y) }) {
            return nearest
        }
        return CGPoint(x: middle.x, y: min(max(middle.y, open.lowerBound), open.upperBound))
    }

    /// How far apart two coordinates are on the ground, in metres: how far a tap reaches, from its point to one a finger's width away.
    static func meters(from a: CLLocationCoordinate2D, to b: CLLocationCoordinate2D) -> Double {
        CLLocation(latitude: a.latitude, longitude: a.longitude).distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
    }
}

/// How a segments map draws its lines, as the shared map draws them.
enum SegmentsMapStyle {
    static let selected = StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round)
    static let lift = StrokeStyle(lineWidth: 2, lineCap: .butt, dash: [6, 4])
    /// A finger's width from a tap, as the shared map's tap box reaches.
    static let tapSlop: CGFloat = 12
}

/// The segments on a plain tile, in Kotlin's drawing order and fit (`SegmentsMap.sketch`): what previews and snapshots show for the map.
private struct SegmentsSketch: View {
    let map: SegmentsMap
    var closed: Set<String> = []
    /// What lies over the tile's top and bottom (the bars, the Trail page's sheet), which a Closed label keeps out of.
    var coveredTop: CGFloat = 0
    var coveredBottom: CGFloat = 0

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
                default:
                    // A dive site's outline stays as it was: the accent colour, with no casing on the plain tile.
                    if line.trail {
                        context.stroke(path, with: .color(TrailRatingStyle.casing(line.rating)), style: TrailRatingStyle.casingStroke)
                    }
                    context.stroke(
                        path, with: .color(TrailRatingStyle.line(line.rating, trail: line.trail)), style: TrailRatingStyle.lineStroke)
                    if TrailRatingStyle.isDouble(line.rating) {
                        context.stroke(path, with: .color(.white), style: TrailRatingStyle.centre)
                    }
                }
            }
        }
        .background(Color(.quaternarySystemFill))
        .overlay {
            if !closed.isEmpty {
                // The sketch keeps the drawn lines' order, so each closed line's middle is found by its place among them.
                GeometryReader { geometry in
                    let drawn = map.drawn.elements(as: MapSegment.self)
                    let placed = map.sketch(width: geometry.size.width, height: geometry.size.height, pad: 16)
                    ForEach(Array(zip(drawn, placed).enumerated()), id: \.offset) { _, pair in
                        let points = pair.1.points.elements(as: ShapePoint.self).map { CGPoint(x: $0.x, y: $0.y) }
                        let open = (coveredTop + 40)...max(coveredTop + 40, geometry.size.height - coveredBottom - 8)
                        if closed.contains(pair.0.uri), let spot = SegmentsMapGeometry.labelSpot(points, open: open) {
                            ClosedBadge().fixedSize().position(x: spot.x, y: spot.y - 14)
                        }
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }
}

/// The label a closed line carries on a map (segments/trail-reports): Closed with its symbol, white on the error colour.
struct ClosedBadge: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "xmark.octagon.fill").accessibilityHidden(true)
            Text(.trailConditionClosed)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(.white)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.red, in: Capsule())
        .overlay(Capsule().strokeBorder(Color(.systemBackground), lineWidth: 1.5))
    }
}
