import SwiftUI

/// One row of a list (ui/ios-design-system): its title, a summary of its value when it has one, and either the system's disclosure
/// mark, when tapping it opens something, or the control that changes it. It goes in the system's `List` and `Section`, which draw the
/// grouped look. A row whose page shows beside its list is `selected`: filled with the system's selection grey, and read as selected.
struct AppListRow<Trailing: View>: View {
    private let title: Text
    private let summary: Text?
    private let icon: AppIcon?
    private let selected: Bool
    private let opens: (() -> Void)?
    private let trailing: Trailing

    /// A row with a control at its trailing edge, or with nothing there when `trailing` is left out of a row that only shows a value.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.summary = summary
        self.icon = icon
        selected = false
        opens = nil
        self.trailing = trailing()
    }

    var body: some View {
        row
            // What UIKit fills a selected cell of a grouped table with; nil leaves the list's own background.
            .listRowBackground(selected ? Color(.systemGray4) : nil)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }

    @ViewBuilder private var row: some View {
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
            // The title keeps its line; a long summary is what wraps.
            title.foregroundStyle(.primary).layoutPriority(1)
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
    /// `selected` marks the row whose page is the one showing beside the list.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil, selected: Bool = false, opens action: @escaping () -> Void) {
        self.title = title
        self.summary = summary
        self.icon = icon
        self.selected = selected
        opens = action
        trailing = EmptyView()
    }

    /// A row that only shows a value.
    init(_ title: Text, summary: Text? = nil, icon: AppIcon? = nil) {
        self.init(title, summary: summary, icon: icon) { EmptyView() }
    }
}
