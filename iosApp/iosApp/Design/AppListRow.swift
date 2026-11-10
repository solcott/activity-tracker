import SwiftUI

/// One row of a list (ui/ios-design-system): its title, a summary of its value when it has one, and either the system's disclosure
/// mark, when tapping it opens something, or the control that changes it. It goes in the system's `List` and `Section`, which draw the
/// grouped look.
struct AppListRow<Trailing: View>: View {
    private let title: Text
    private let summary: Text?
    private let icon: AppIcon?
    private let opens: (() -> Void)?
    private let trailing: Trailing

    /// A row with a control at its trailing edge, or with nothing there when `trailing` is left out of a row that only shows a value.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.summary = summary
        self.icon = icon
        opens = nil
        self.trailing = trailing()
    }

    var body: some View {
        if let opens {
            // The whole row is the button, so a tap anywhere on it opens what it opens.
            Button(action: opens) { content.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: 12) {
            if let icon {
                Image(icon).foregroundStyle(.tint).frame(minWidth: 28)
            }
            title.foregroundStyle(.primary)
            Spacer(minLength: 8)
            summary?.foregroundStyle(.secondary).multilineTextAlignment(.trailing)
            if opens != nil {
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            } else {
                trailing
            }
        }
    }
}

extension AppListRow where Trailing == EmptyView {
    /// A row that opens something when tapped. `opens` is written out, not trailing, so it can't be read as a row's control.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil, opens action: @escaping () -> Void) {
        self.title = title
        self.summary = summary
        self.icon = icon
        opens = action
        trailing = EmptyView()
    }

    /// A row that only shows a value.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil) {
        self.init(title, summary: summary, icon: icon) { EmptyView() }
    }
}
