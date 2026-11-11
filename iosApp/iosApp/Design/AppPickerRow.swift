import SwiftUI

/// A choice among `options` as a row of the system's grouped form (ui/ios-design-system): the label leading, wrapping when long, and the
/// chosen option with the system's up-down mark at the trailing end on one line, opening a menu of the options with the chosen one
/// checked. `name` is what VoiceOver calls it, and each option is shown as `optionLabel` words it.
///
/// The row is laid out here rather than by the form: given a label too long for one line, the form's own picker row moves the choice
/// under the label. A menu holding the picker, rather than a menu picker beside the label, keeps the form's row height: a menu picker
/// outside the form's row layout brings a button's padding of its own.
struct AppPickerRow<Value: Hashable, Label: View>: View {
    private let label: Label
    private let name: String
    private let selection: Binding<Value>
    private let options: [Value]
    private let optionLabel: (Value) -> String

    init(
        name: String, selection: Binding<Value>, options: [Value], optionLabel: @escaping (Value) -> String,
        @ViewBuilder label: () -> Label
    ) {
        self.label = label()
        self.name = name
        self.selection = selection
        self.options = options
        self.optionLabel = optionLabel
    }

    var body: some View {
        let shown = optionLabel(selection.wrappedValue)
        HStack(spacing: 12) {
            label.frame(maxWidth: .infinity, alignment: .leading).accessibilityHidden(true)
            Menu {
                Picker(selection: selection) {
                    ForEach(options, id: \.self) { option in
                        Text(verbatim: optionLabel(option)).tag(option)
                    }
                } label: {
                    Text(verbatim: name)
                }
            } label: {
                HStack(spacing: 4) {
                    Text(verbatim: shown)
                    Image(systemName: "chevron.up.chevron.down").font(.footnote.weight(.medium))
                }
            }
            .fixedSize()
            .accessibilityLabel(Text(verbatim: name))
            .accessibilityValue(Text(verbatim: shown))
        }
    }
}

extension AppPickerRow where Label == Text {
    /// A row labelled with its name.
    init(_ name: String, selection: Binding<Value>, options: [Value], optionLabel: @escaping (Value) -> String) {
        self.init(name: name, selection: selection, options: options, optionLabel: optionLabel) { Text(verbatim: name) }
    }
}
