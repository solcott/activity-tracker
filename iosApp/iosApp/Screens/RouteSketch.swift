import SwiftUI

/// An activity's route as the line preview draws it (feed/feed-route-map), on a tile of the system's quaternary fill: each piece
/// of the line in the accent colour, a dashed connector across each break with its stop and resume markers, and the start and end
/// markers. Where everything goes is Kotlin's (`RouteShape`, which smooths and fits the route the viewer was served into the tile's
/// size); this only strokes it, so the route is drawn on the device and sent nowhere. Asked again whenever the tile's size changes.
struct RouteSketch: View {
    /// The route placed on a tile of a size: the card's `routeShape`.
    let shape: (CGSize) -> RouteShape?

    var body: some View {
        Canvas { context, size in
            guard let shape = shape(size) else { return }
            draw(shape, in: &context)
        }
        .background(Color(.quaternarySystemFill))
        .accessibilityHidden(true)
    }

    private func draw(_ shape: RouteShape, in context: inout GraphicsContext) {
        var line = Path()
        for piece in shape.pieces.elements(as: RoutePiece.self) {
            let points = piece.points.elements(as: ShapePoint.self).map(Self.point)
            guard let first = points.first else { continue }
            line.move(to: first)
            for point in points.dropFirst() { line.addLine(to: point) }
        }
        context.stroke(line, with: .color(.accentColor), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        let breaks = shape.breaks.elements(as: RouteBreak.self)
        for gap in breaks {
            var connector = Path()
            connector.move(to: Self.point(gap.stop))
            connector.addLine(to: Self.point(gap.resume))
            context.stroke(
                connector, with: .color(Marker.breakColor),
                style: StrokeStyle(lineWidth: Marker.connectorWidth, dash: [Marker.dash, Marker.space]))
        }
        for gap in breaks {
            marker(at: gap.stop, fill: Marker.breakColor, ring: .white, in: &context)
            marker(at: gap.resume, fill: .white, ring: Marker.breakColor, ringWidth: Marker.resumeRing, in: &context)
        }
        // A map whose ends are hidden (a dive site's outline) marks neither.
        if let start = shape.start { marker(at: start, fill: Marker.startColor, ring: .white, in: &context) }
        if let end = shape.end { marker(at: end, fill: Marker.endColor, ring: .white, in: &context) }
    }

    /// A dot filled with `fill` and ringed with `ring`, as the shared preview draws its markers.
    private func marker(
        at point: ShapePoint, fill: Color, ring: Color, ringWidth: CGFloat = Marker.ring, in context: inout GraphicsContext
    ) {
        let center = Self.point(point)
        context.fill(Path(ellipseIn: Self.circle(center, Marker.radius)), with: .color(ring))
        context.fill(Path(ellipseIn: Self.circle(center, Marker.radius - ringWidth)), with: .color(fill))
    }

    private static func point(_ point: ShapePoint) -> CGPoint { CGPoint(x: point.x, y: point.y) }

    private static func circle(_ center: CGPoint, _ radius: CGFloat) -> CGRect {
        CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
    }

    /// The markers' look, copied from the shared UI's `RouteMarkers` so both previews draw the same.
    private enum Marker {
        static let startColor = Color(red: 0x43 / 255, green: 0xA0 / 255, blue: 0x47 / 255)
        static let endColor = Color(red: 0xE5 / 255, green: 0x39 / 255, blue: 0x35 / 255)
        static let breakColor = Color(red: 1, green: 0xA0 / 255, blue: 0)
        static let radius: CGFloat = 7
        static let ring: CGFloat = 2
        static let resumeRing: CGFloat = 3
        static let connectorWidth: CGFloat = 2.5
        static let dash: CGFloat = 8
        static let space: CGFloat = 5
    }
}
