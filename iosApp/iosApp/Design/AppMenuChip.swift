import SwiftUI

/// A filter over a list (ui/ios-design-system): a capsule naming the option chosen, with a chevron, that opens a menu of `options`
/// with the chosen one checked. It is filled while `filled` (its choice narrows the list) and plain while the choice shows everything,
/// as the shared filter chip is selected or not. `name`, when given, is what VoiceOver calls it, with the choice as its value; without
/// one VoiceOver reads the choice. Each option is shown as `optionLabel` words it.
///
/// A menu holding an inline picker, as `AppPickerRow`'s, so the menu checks the chosen option itself.
struct AppMenuChip<Value: Hashable>: View {
    private let name: String?
    private let filled: Bool
    private let selection: Binding<Value>
    private let options: [Value]
    private let optionLabel: (Value) -> String

    init(
        _ name: String?, filled: Bool, selection: Binding<Value>, options: [Value], optionLabel: @escaping (Value) -> String
    ) {
        self.name = name
        self.filled = filled
        self.selection = selection
        self.options = options
        self.optionLabel = optionLabel
    }

    var body: some View {
        let shown = optionLabel(selection.wrappedValue)
        Menu {
            Picker(selection: selection) {
                ForEach(options, id: \.self) { option in
                    Text(verbatim: optionLabel(option)).tag(option)
                }
            } label: {
                Text(verbatim: name ?? shown)
            }
            .pickerStyle(.inline)
        } label: {
            HStack(spacing: 4) {
                Text(verbatim: shown).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption.weight(.semibold)).accessibilityHidden(true)
            }
            .chipLabel(filled: filled)
        }
        .chipStyle(filled: filled)
        .accessibilityLabel(Text(verbatim: name ?? shown))
        .accessibilityValue(Text(verbatim: name == nil ? "" : shown))
    }
}

/// A filter chip that acts rather than opens a menu (ui/ios-design-system): a capsule button, filled while `filled` (its choice narrows
/// the list, or it is the one chosen), as the shared filter chip is selected or not. A chip that opens a menu is `AppMenuChip`.
struct AppChip<Label: View>: View {
    let filled: Bool
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    var body: some View {
        Button(action: action) {
            label().lineLimit(1).chipLabel(filled: filled)
        }
        .chipStyle(filled: filled)
    }
}

extension View {
    /// A chip's label: on the fill the colour that reads on it, as `AppButton`'s label (the system's white doesn't on the dark theme's
    /// violet), else the tint.
    func chipLabel(filled: Bool) -> some View {
        font(.subheadline.weight(.medium))
            .foregroundStyle(filled ? AnyShapeStyle(Color(.onAccent)) : AnyShapeStyle(.tint))
    }

    /// A capsule button at its own size, filled in the app's colour or tinted, as a filter chip is drawn.
    @ViewBuilder func chipStyle(filled: Bool) -> some View {
        if filled {
            buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
        } else {
            buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
        }
    }
}
