import SwiftUI

/// Training zones' fixed palette (DESIGN.md "Training zones"), as the shared `ZoneBars` colours them: the lowest zone recovery grey,
/// then blue, green, yellow, orange, red and purple, dropping the middle colours when there are fewer zones. Each is a catalog colour,
/// at Material's 400 tone on a light surface and its 300 tone on a dark one, as the grade bands step.
enum ZoneColors {
    private static let recovery = Color(.zoneRecovery)
    private static let blue = Color(.zoneBlue)
    private static let green = Color(.zoneGreen)
    private static let yellow = Color(.zoneYellow)
    private static let orange = Color(.zoneOrange)
    private static let red = Color(.zoneRed)
    private static let purple = Color(.zonePurple)
    private static let hot = [blue, green, yellow, orange, red, purple]

    /// The colour of zone `index` (0 the lowest) of `count`.
    static func color(_ index: Int, of count: Int) -> Color {
        if index == 0 { return recovery }
        let colors: [Color]
        switch count - 1 {
        case hot.count...: colors = hot
        case hot.count - 1: colors = Array(hot.dropLast())
        case hot.count - 2: colors = [blue, green, orange, red]
        default: colors = [green, orange, red]
        }
        return colors[min(index - 1, colors.count - 1)]
    }
}
