import SwiftUI

/// Training zones' fixed palette (DESIGN.md "Training zones"), as the shared `ZoneBars` colours them. Which tone a zone takes is Kotlin's
/// (`ZoneTone.of`, carried by each `ZoneRow`); this only says what colour each tone is. Each is a catalog colour, at Material's 400 tone
/// on a light surface and its 300 tone on a dark one, as the grade bands step.
enum ZoneColors {
    static func color(_ tone: ZoneTone) -> Color {
        switch tone {
        case .Recovery: Color(.zoneRecovery)
        case .Blue: Color(.zoneBlue)
        case .Green: Color(.zoneGreen)
        case .Yellow: Color(.zoneYellow)
        case .Orange: Color(.zoneOrange)
        case .Red: Color(.zoneRed)
        case .Purple: Color(.zonePurple)
        }
    }
}
