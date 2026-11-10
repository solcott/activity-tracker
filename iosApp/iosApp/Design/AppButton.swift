import SwiftUI

/// A screen's main action: the filled button in the app's colour (ui/ios-design-system).
///
/// Its label is drawn in the colour that reads on the fill (`OnAccentColor`, the theme's on-primary), since the system's white label
/// doesn't on the dark theme's light teal.
struct AppButton: View {
    private let title: LocalizedStringResource
    private let action: () -> Void

    init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) { AppButtonLabel(title: title) }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
    }
}

private struct AppButtonLabel: View {
    let title: LocalizedStringResource
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        // A disabled button keeps the system's dimmed label.
        if isEnabled {
            Text(title).foregroundStyle(Color(.onAccent))
        } else {
            Text(title)
        }
    }
}

/// An action beside the main one, or a screen's only action when it isn't the point of the screen: tinted, not filled.
struct AppSecondaryButton: View {
    private let title: LocalizedStringResource
    private let action: () -> Void

    init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) { Text(title) }
            .buttonStyle(.bordered)
            .controlSize(.large)
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

/// A button that shows only an icon. VoiceOver reads `label`, which every icon button must have.
struct AppIconButton: View {
    private let icon: AppIcon
    private let label: LocalizedStringResource
    private let action: () -> Void

    init(_ icon: AppIcon, label: LocalizedStringResource, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.action = action
    }

    var body: some View {
        Button(action: action) { Image(icon) }
            .buttonStyle(.borderless)
            // The system's smallest comfortable target, whatever the symbol's own size.
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .accessibilityLabel(Text(label))
    }
}
