import SwiftUI

/// A screen's main action: the filled button in the app's colour (ui/ios-design-system).
///
/// Its label is drawn in the colour that reads on the fill (`OnAccentColor`, the theme's on-primary), since the system's white label
/// doesn't on the dark theme's light violet.
struct AppButton: View {
    private let button: AppStyledButton

    /// `small` is the size for a list row's trailing end (Approve), and the default the size of a screen's own buttons.
    init(_ title: LocalizedStringResource, small: Bool = false, action: @escaping () -> Void) {
        button = AppStyledButton(title: Text(title), filled: true, small: small, wide: false, action: action)
    }

    /// A button whose label Kotlin chose (a profile's Follow). `wide` fills the width it's given, as a bar of controls shares it
    /// (Record's).
    init(_ title: any AppText, small: Bool = false, wide: Bool = false, action: @escaping () -> Void) {
        button = AppStyledButton(title: Text(title), filled: true, small: small, wide: wide, action: action)
    }

    var body: some View { button }
}

/// An action beside the main one, or a screen's only action when it isn't the point of the screen: tinted, not filled.
struct AppSecondaryButton: View {
    private let button: AppStyledButton

    /// `small` is the size for a list row's trailing end (Unblock), and the default the size of a screen's own buttons.
    init(_ title: LocalizedStringResource, small: Bool = false, action: @escaping () -> Void) {
        button = AppStyledButton(title: Text(title), filled: false, small: small, wide: false, action: action)
    }

    /// A button whose label Kotlin chose. `wide` fills the width it's given, as `AppButton`'s does.
    init(_ title: any AppText, small: Bool = false, wide: Bool = false, action: @escaping () -> Void) {
        button = AppStyledButton(title: Text(title), filled: false, small: small, wide: wide, action: action)
    }

    var body: some View { button }
}

/// What `AppButton` and `AppSecondaryButton` share: the app's colour filled, or tinted, at a screen's or a row's size.
private struct AppStyledButton: View {
    let title: Text
    let filled: Bool
    let small: Bool
    let wide: Bool
    let action: () -> Void

    var body: some View {
        if filled {
            Button(action: action) { AppButtonLabel(title: title).wideLabel(wide) }
                .buttonStyle(.borderedProminent)
                .controlSize(small ? .small : .large)
        } else {
            Button(action: action) { title.wideLabel(wide) }
                .buttonStyle(.bordered)
                .controlSize(small ? .small : .large)
        }
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

/// An action in line with what it acts on, drawn as text in the app's colour: Retry beside an error, Cancel under a notice.
struct AppTextButton: View {
    private let title: Text
    private let action: () -> Void

    init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = Text(title)
        self.action = action
    }

    /// A button whose words Kotlin chose (`AppText`), such as the Trail page's Expand all or Collapse all.
    init(_ title: any AppText, action: @escaping () -> Void) {
        self.title = Text(title)
        self.action = action
    }

    var body: some View {
        Button(action: action) { title }
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

    /// An icon button with its label already made, for a Design view that wraps one (`LikeButton`).
    init(_ icon: AppIcon, labelText: Text, filled: Bool = false, action: @escaping () -> Void) {
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

/// An icon-only action that repeats while held, such as a nudge: `onStep` hears 0 for the press and, while it stays pressed, how many steps
/// came before each repeat. Each press starts again at 0, however soon after the last, so quick taps never count as a hold.
struct AppRepeatIconButton: View {
    private let icon: AppIcon
    private let label: Text
    private let onStep: (Int32) -> Void
    @State private var repeats = RepeatCount()

    init(_ icon: AppIcon, label: any AppText, onStep: @escaping (Int32) -> Void) {
        self.icon = icon
        self.label = Text(label)
        self.onStep = onStep
    }

    var body: some View {
        Button { onStep(repeats.next(at: .now)) } label: { Image(icon, filled: false) }
            .buttonStyle(RepeatButtonStyle { repeats.released() })
            .buttonRepeatBehavior(.enabled)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .accessibilityLabel(label)
    }
}

/// How many steps a held repeating button has taken: SwiftUI repeats its action without saying which step it is, so the count grows
/// with each action until the press ends (`released`), or, for an action with no press (VoiceOver's), until `gap` passes without one.
struct RepeatCount {
    static let gap: TimeInterval = 0.7
    private var last: Date?
    private var count: Int32 = 0

    mutating func next(at now: Date) -> Int32 {
        if let last, now.timeIntervalSince(last) < Self.gap {
            count += 1
        } else {
            count = 0
        }
        last = now
        return count
    }

    mutating func released() {
        last = nil
    }
}

/// A borderless button's look that also says when a press ends, which `AppRepeatIconButton` needs to start each press at 0.
private struct RepeatButtonStyle: ButtonStyle {
    let released: () -> Void

    func makeBody(configuration: Configuration) -> some View {
        RepeatButtonBody(configuration: configuration, released: released)
    }
}

private struct RepeatButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let released: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        configuration.label
            .foregroundStyle(isEnabled ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
            .opacity(configuration.isPressed ? 0.3 : 1)
            .onChange(of: configuration.isPressed) { _, pressed in
                if !pressed { released() }
            }
    }
}

extension View {
    /// A wide button's label fills the width it's given.
    fileprivate func wideLabel(_ wide: Bool) -> some View {
        frame(maxWidth: wide ? .infinity : nil)
    }
}
