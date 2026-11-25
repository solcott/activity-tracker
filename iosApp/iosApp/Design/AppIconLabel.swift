import SwiftUI

/// A chrome icon as the label of a menu or button that draws its own (a row's More menu, a gallery's Close): the symbol in the system's
/// smallest comfortable target, `size` points (44 unless a tight row asks for less), all of which takes taps. Like `Image(AppIcon)`, it
/// takes the colour and scale its view gives it; the menu or button carries the accessibility label.
struct AppIconLabel: View {
    private let icon: AppIcon
    private let size: CGFloat

    init(_ icon: AppIcon, size: CGFloat = 44) {
        self.icon = icon
        self.size = size
    }

    var body: some View {
        Image(icon).frame(minWidth: size, minHeight: size).contentShape(Rectangle())
    }
}
