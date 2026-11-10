import SwiftUI

extension Image {
    /// One of the app's chrome icons, as its SF Symbol: the filled one for a selected or active state. Like any symbol, it takes the
    /// size of the text around it and the colour its view gives it.
    init(_ icon: AppIcon, filled: Bool = false) {
        self.init(systemName: filled ? icon.filledSymbol : icon.symbol)
    }

    /// One of the shared UI's vendored drawables, by its name there (dev/ios-native-resources): a template image, so it takes the
    /// colour its view gives it.
    init(drawable name: String) {
        self.init("Drawable/\(name)")
    }
}

/// A sport's image, as the shared UI draws it: its sub-sport's, else its sport's, else a generic one. Which image is Kotlin's
/// `SportIconNames`, the rule the shared UI follows too. It grows with the text beside it.
struct SportIcon: View {
    private let name: String
    @ScaledMetric private var size: CGFloat

    init(_ type: KotlinActivityType, size: CGFloat = 24) {
        name = SportIconNames.shared.of(type: type)
        _size = ScaledMetric(wrappedValue: size, relativeTo: .body)
    }

    var body: some View {
        Image(drawable: name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            // Decorative: it always sits beside the type's name.
            .accessibilityHidden(true)
    }
}
