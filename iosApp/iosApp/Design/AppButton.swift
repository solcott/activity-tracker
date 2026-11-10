import SwiftUI

/// A screen's main action: the filled button in the app's colour.
///
/// Its label is drawn in the colour that reads on the fill (`OnAccentColor`, the theme's on-primary), since the system's white label
/// doesn't on the dark theme's light teal.
struct AppButton: View {
    let title: LocalizedStringKey
    let action: () -> Void

    init(_ title: LocalizedStringKey, action: @escaping () -> Void) {
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
    let title: LocalizedStringKey
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
