import SwiftUI

/// The gap between the cards of a page drawn as an inset grouped list, one card to a section (the feed, an activity, a profile, a post, a
/// segment, a dive site): the shared UI's `ITEM_GAP`, where the list's own section spacing, meant for a settings form's groups, would
/// set them three times as far apart. Forms and settings pages keep the system's spacing.
enum CardSpacing {
    static let gap: CGFloat = 12
}

extension View {
    /// Spaces the sections of the lists in this view as a card page's: `CardSpacing.gap` apart.
    func cardSpacing() -> some View {
        listSectionSpacing(CardSpacing.gap)
    }
}
