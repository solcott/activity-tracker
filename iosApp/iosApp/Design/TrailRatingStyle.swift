import SwiftUI

/// How a map draws a run by its rating level (segments/trail-ratings, ui/ios-design-system), as the shared `TrailRatingColors` does: white,
/// green, blue, black and a double black line, each on a casing that stands out from it, and an unrated run grey. The catalog colours hold
/// the same values for light and dark. The level is Kotlin's (`TrailRating.of`), never worked out here.
enum TrailRatingStyle {
    /// A run's line colour, or the unrated grey for nil; the accent colour for a line that isn't a trail (a dive site's outline). By
    /// name: inside a map's content a catalog colour must be looked up so, and `Color.accentColor` is the system's blue there.
    static func line(_ rating: TrailRating?, trail: Bool = true) -> Color {
        guard trail else { return Color("AccentColor") }
        guard let rating else { return Color("TrailUnratedColor") }
        switch rating {
        case .EASIEST: return Color("TrailRating0Color")
        case .EASY: return Color("TrailRating1Color")
        case .INTERMEDIATE: return Color("TrailRating2Color")
        case .DIFFICULT: return Color("TrailRating3Color")
        default: return Color("TrailRating4Color")
        }
    }

    /// The casing under a run: dark under Easiest's white line, white under every other.
    static func casing(_ rating: TrailRating?) -> Color {
        rating == .EASIEST ? Color("TrailRatingCasing0Color") : .white
    }

    /// Whether the run's black line is drawn double, with a thin white line down its middle.
    static func isDouble(_ rating: TrailRating?) -> Bool { rating == .EXTREME }

    static let lineStroke = StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
    static let casingStroke = StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round)
    static let centre = StrokeStyle(lineWidth: 4.0 / 3, lineCap: .round, lineJoin: .round)
}

/// A short sample of how a map draws a level's line, read as the level's name; nothing for an unrated row unless `showsUnrated`.
struct RatingSwatch: View {
    let rating: TrailRating?
    var showsUnrated = false

    var body: some View {
        if rating != nil || showsUnrated {
            Canvas { context, size in
                var path = Path()
                path.move(to: CGPoint(x: 3, y: size.height / 2))
                path.addLine(to: CGPoint(x: size.width - 3, y: size.height / 2))
                context.stroke(path, with: .color(TrailRatingStyle.casing(rating)), style: TrailRatingStyle.casingStroke)
                context.stroke(path, with: .color(TrailRatingStyle.line(rating)), style: TrailRatingStyle.lineStroke)
                if TrailRatingStyle.isDouble(rating) {
                    context.stroke(path, with: .color(.white), style: TrailRatingStyle.centre)
                }
            }
            .frame(width: 20, height: 8)
            .accessibilityLabel(rating.map { Text($0.label) } ?? Text(.trailRatingUnrated))
        }
    }
}

/// A map's legend: the levels Kotlin says its runs are drawn in (`SegmentsMap.legend`), each with its swatch and name, on a small card in
/// the map's corner. Nothing when no run is rated.
struct RatingLegendView: View {
    let map: SegmentsMap

    var body: some View {
        let entries = map.legend.elements(as: RatingLegendEntry.self)
        if !entries.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                    HStack(spacing: 6) {
                        RatingSwatch(rating: entry.rating, showsUnrated: true).accessibilityHidden(true)
                        Text(entry.label).font(.caption2)
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
            .accessibilityElement(children: .combine)
        }
    }
}
