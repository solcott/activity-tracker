import SwiftUI

/// A choice in a list that is ticked while it is chosen (ui/ios-design-system): its label, then the system's checkmark in the tint at
/// the trailing edge, read as selected. A tap anywhere on the row runs `action`, which chooses it or, for a set, toggles it.
/// `spacing` is the row's own, between the label's pieces (a sport's icon and its name) and before the checkmark.
struct AppCheckRow<Label: View>: View {
    private let checked: Bool
    private let spacing: CGFloat?
    private let action: () -> Void
    private let label: Label

    init(checked: Bool, spacing: CGFloat? = nil, action: @escaping () -> Void, @ViewBuilder label: () -> Label) {
        self.checked = checked
        self.spacing = spacing
        self.action = action
        self.label = label()
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: spacing) {
                label
                Spacer()
                if checked {
                    Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        // Plain, so the label keeps its own colours and only this row takes the tap.
        .buttonStyle(.plain)
        .accessibilityAddTraits(checked ? .isSelected : [])
    }
}
