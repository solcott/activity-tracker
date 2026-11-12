import SwiftUI

/// A screen's main action: the filled button in the app's colour (ui/ios-design-system).
///
/// Its label is drawn in the colour that reads on the fill (`OnAccentColor`, the theme's on-primary), since the system's white label
/// doesn't on the dark theme's light teal.
struct AppButton: View {
    private let title: Text
    private let small: Bool
    private let wide: Bool
    private let action: () -> Void

    /// `small` is the size for a list row's trailing end (Approve), and the default the size of a screen's own buttons.
    init(_ title: LocalizedStringResource, small: Bool = false, action: @escaping () -> Void) {
        self.init(text: Text(title), small: small, wide: false, action: action)
    }

    /// A button whose label Kotlin chose (a profile's Follow). `wide` fills the width it's given, as a bar of controls shares it
    /// (Record's).
    init(_ title: any AppText, small: Bool = false, wide: Bool = false, action: @escaping () -> Void) {
        self.init(text: Text(title), small: small, wide: wide, action: action)
    }

    private init(text: Text, small: Bool, wide: Bool, action: @escaping () -> Void) {
        title = text
        self.small = small
        self.wide = wide
        self.action = action
    }

    var body: some View {
        Button(action: action) { AppButtonLabel(title: title).wideLabel(wide) }
            .buttonStyle(.borderedProminent)
            .controlSize(small ? .small : .large)
    }
}

private struct AppButtonLabel: View {
    let title: Text
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        // A disabled button keeps the system's dimmed label.
        if isEnabled {
            title.foregroundStyle(Color(.onAccent))
        } else {
            title
        }
    }
}

/// An action beside the main one, or a screen's only action when it isn't the point of the screen: tinted, not filled.
struct AppSecondaryButton: View {
    private let title: Text
    private let small: Bool
    private let wide: Bool
    private let action: () -> Void

    /// `small` is the size for a list row's trailing end (Unblock), and the default the size of a screen's own buttons.
    init(_ title: LocalizedStringResource, small: Bool = false, action: @escaping () -> Void) {
        self.init(text: Text(title), small: small, wide: false, action: action)
    }

    /// A button whose label Kotlin chose. `wide` fills the width it's given, as `AppButton`'s does.
    init(_ title: any AppText, small: Bool = false, wide: Bool = false, action: @escaping () -> Void) {
        self.init(text: Text(title), small: small, wide: wide, action: action)
    }

    private init(text: Text, small: Bool, wide: Bool, action: @escaping () -> Void) {
        title = text
        self.small = small
        self.wide = wide
        self.action = action
    }

    var body: some View {
        Button(action: action) { title.wideLabel(wide) }
            .buttonStyle(.bordered)
            .controlSize(small ? .small : .large)
    }
}

/// An action in line with what it acts on, drawn as text in the app's colour: Retry beside an error, Cancel under a notice.
struct AppTextButton: View {
    private let title: LocalizedStringResource
    private let action: () -> Void

    init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) { Text(title) }
            .buttonStyle(.borderless)
    }
}

/// A button that shows only an icon. VoiceOver reads `label`, which every icon button must have. `filled` draws the icon's selected
/// or active form (a starred segment's star).
struct AppIconButton: View {
    private let icon: AppIcon
    private let label: Text
    private let filled: Bool
    private let action: () -> Void

    init(_ icon: AppIcon, label: LocalizedStringResource, filled: Bool = false, action: @escaping () -> Void) {
        self.init(icon, labelText: Text(label), filled: filled, action: action)
    }

    /// An icon button whose label Kotlin chooses (a post's Undo repost, a vote naming its proposal).
    init(_ icon: AppIcon, label: any AppText, filled: Bool = false, action: @escaping () -> Void) {
        self.init(icon, labelText: Text(label), filled: filled, action: action)
    }

    private init(_ icon: AppIcon, labelText: Text, filled: Bool, action: @escaping () -> Void) {
        self.icon = icon
        self.label = labelText
        self.filled = filled
        self.action = action
    }

    var body: some View {
        Button(action: action) { Image(icon, filled: filled) }
            .buttonStyle(.borderless)
            // The system's smallest comfortable target, whatever the symbol's own size.
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .accessibilityLabel(label)
    }
}

extension View {
    /// A wide button's label fills the width it's given.
    fileprivate func wideLabel(_ wide: Bool) -> some View {
        frame(maxWidth: wide ? .infinity : nil)
    }
}
