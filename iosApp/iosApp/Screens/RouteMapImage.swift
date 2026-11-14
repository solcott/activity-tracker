import MapKit
import SwiftUI

/// A feed card's route as a still map (feed/feed-route-map): the system map's snapshot of the frame Kotlin gives the route for the
/// tile's size (`RouteMap.frame`, as the live map and the other platforms' images frame it), in the current appearance, with the route
/// drawn over it as `RouteMapView` draws it: the line on a white casing, a highlighted stretch, dashed connectors across breaks, and the
/// break, start and end markers. Only the map's tiles are fetched; the route is drawn here and sent nowhere.
///
/// The line preview stands in while the image draws and whenever it can't be (no network, no tiles), with no error. Images are kept in
/// memory by route, size and appearance (`RouteMapImages`), so a card scrolled back to shows its image at once.
struct RouteMapImage: View {
    let map: RouteMap

    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var scale
    @State private var size: CGSize = .zero
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable()
            } else {
                RouteSketch { size in map.sketch(width: size.width, height: size.height, pad: RouteMarkerStyle.sketchPad) }
            }
        }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
        .task(id: key) { await draw() }
        .accessibilityHidden(true)
    }

    private var key: String { RouteMapImages.key(map, size: size, dark: scheme == .dark) }

    private func draw() async {
        guard size.width >= 1, size.height >= 1 else { return }
        if let cached = RouteMapImages.cached(key) {
            image = cached
            return
        }
        image = nil
        let drawn = await RouteMapImages.draw(map, size: size, dark: scheme == .dark, scale: scale)
        guard let drawn, !Task.isCancelled else { return }
        RouteMapImages.keep(drawn, for: key)
        image = drawn
    }
}

/// Drawing and keeping feed map images.
enum RouteMapImages {
    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 60
        return cache
    }()

    static func key(_ map: RouteMap, size: CGSize, dark: Bool) -> String {
        "\(map.key)|\(Int(size.width.rounded()))x\(Int(size.height.rounded()))|\(dark ? "dark" : "light")"
    }

    static func cached(_ key: String) -> UIImage? { cache.object(forKey: key as NSString) }

    static func keep(_ image: UIImage, for key: String) { cache.setObject(image, forKey: key as NSString) }

    /// The map under `map`'s route at `size`, with the route drawn over it; nil when the snapshot fails.
    static func draw(_ map: RouteMap, size: CGSize, dark: Bool, scale: CGFloat) async -> UIImage? {
        let traits = UITraitCollection { traits in
            traits.userInterfaceStyle = dark ? .dark : .light
            traits.displayScale = scale
        }
        let options = MKMapSnapshotter.Options()
        options.mapRect = MapCamera.rect(map.frame(width: size.width, height: size.height, top: 0), size: size)
        options.size = size
        options.traitCollection = traits
        options.preferredConfiguration = MKStandardMapConfiguration(emphasisStyle: .default)
        options.pointOfInterestFilter = .excludingAll
        // Kotlin's route as plain coordinates, read before the snapshot is awaited.
        let route = Route(map)
        let snapshot: MKMapSnapshotter.Snapshot
        do {
            snapshot = try await MKMapSnapshotter(options: options).start()
        } catch {
            return nil
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        return UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            snapshot.image.draw(at: .zero)
            route.draw(in: renderer.cgContext, at: snapshot.point(for:), traits: traits)
        }
    }

    /// A route's geometry, copied out of Kotlin's `RouteMap`.
    private struct Route {
        let pieces: [[CLLocationCoordinate2D]]
        let highlight: [CLLocationCoordinate2D]?
        let breaks: [(stop: CLLocationCoordinate2D, resume: CLLocationCoordinate2D)]
        let start: CLLocationCoordinate2D?
        let end: CLLocationCoordinate2D?

        init(_ map: RouteMap) {
            pieces = map.pieces.elements(as: MapLine.self).map { $0.points.elements(as: GeoPoint.self).map(Self.coordinate) }
            highlight = map.highlight.map { $0.points.elements(as: GeoPoint.self).map(Self.coordinate) }
            breaks = map.breaks.elements(as: MapBreak.self).map { (Self.coordinate($0.stop), Self.coordinate($0.resume)) }
            start = map.start.map(Self.coordinate)
            end = map.end.map(Self.coordinate)
        }

        private static func coordinate(_ point: GeoPoint) -> CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
        }

        func draw(in context: CGContext, at point: (CLLocationCoordinate2D) -> CGPoint, traits: UITraitCollection) {
            let accent = (UIColor(named: "AccentColor") ?? .tintColor).resolvedColor(with: traits)
            let highlightColor = (UIColor(named: "LikeColor") ?? .systemPink).resolvedColor(with: traits)
            let breakColor = UIColor(RouteMarkerStyle.breakColor)
            for line in pieces {
                stroke(line, in: context, at: point, color: .white, width: RouteMarkerStyle.casing.lineWidth)
            }
            for line in pieces {
                stroke(line, in: context, at: point, color: accent, width: RouteMarkerStyle.line.lineWidth)
            }
            if let highlight {
                stroke(highlight, in: context, at: point, color: .white, width: RouteMarkerStyle.highlightCasing.lineWidth)
                stroke(highlight, in: context, at: point, color: highlightColor, width: RouteMarkerStyle.highlight.lineWidth)
            }
            for gap in breaks {
                context.saveGState()
                context.setLineDash(phase: 0, lengths: [RouteMarkerStyle.dash, RouteMarkerStyle.space])
                stroke([gap.stop, gap.resume], in: context, at: point, color: breakColor, width: RouteMarkerStyle.connectorWidth, round: false)
                context.restoreGState()
            }
            for gap in breaks {
                marker(at: point(gap.stop), fill: breakColor, ring: .white, in: context)
                marker(at: point(gap.resume), fill: .white, ring: breakColor, ringWidth: RouteMarkerStyle.resumeRing, in: context)
            }
            if let start { marker(at: point(start), fill: UIColor(RouteMarkerStyle.startColor), ring: .white, in: context) }
            if let end { marker(at: point(end), fill: UIColor(RouteMarkerStyle.endColor), ring: .white, in: context) }
        }

        private func stroke(
            _ line: [CLLocationCoordinate2D], in context: CGContext, at point: (CLLocationCoordinate2D) -> CGPoint, color: UIColor,
            width: CGFloat, round: Bool = true
        ) {
            guard let first = line.first else { return }
            context.beginPath()
            context.move(to: point(first))
            for coordinate in line.dropFirst() { context.addLine(to: point(coordinate)) }
            context.setStrokeColor(color.cgColor)
            context.setLineWidth(width)
            context.setLineCap(round ? .round : .butt)
            context.setLineJoin(.round)
            context.strokePath()
        }

        private func marker(
            at center: CGPoint, fill: UIColor, ring: UIColor, ringWidth: CGFloat = RouteMarkerStyle.ring, in context: CGContext
        ) {
            let radius = RouteMarkerStyle.radius
            context.setFillColor(ring.cgColor)
            context.fillEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
            let inner = radius - ringWidth
            context.setFillColor(fill.cgColor)
            context.fillEllipse(in: CGRect(x: center.x - inner, y: center.y - inner, width: inner * 2, height: inner * 2))
        }
    }
}
