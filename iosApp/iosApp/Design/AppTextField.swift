import SwiftUI

/// How a field's keyboard behaves.
struct TextFieldOptions {
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .sentences
    var autocorrection = true
    var contentType: UITextContentType?
    var submitLabel: SubmitLabel = .done
}

/// A text field over a Kotlin text field state, with its label and, when there is some, the text under it that explains it
/// (dev/ios-swift-interop, ui/ios-design-system). A field the screen marks as wrong shows that text in the error colour.
///
/// `layout` says where it sits: on its own, with its label above a filled box (sign-in); as a row of the system's grouped form, its label
/// leading and its text trailing on one line; or in a form with its label above text that grows to several lines (notes).
///
/// `hint` shows where the text goes while there is none, in the placeholder's colour (a sport's threshold: the default a blank one
/// falls back to), and VoiceOver reads it with the label.
///
/// The screen holds the model, so the rest of it can read the text as it is typed (a button enabled only once there is some). The
/// field keeps the model on the state it is given: when a presenter replaces the state object, the model moves to the new one.
struct AppTextField: View {
    enum Layout {
        /// The label above a filled box, for a field on its own.
        case stacked
        /// A row of a grouped form: the label leading, the text trailing on one line.
        case row
        /// In a grouped form, the label above text that grows to several lines.
        case multiline
    }

    private let label: Text
    private let model: TextFieldModel
    private let state: KotlinTextFieldState
    private let layout: Layout
    private let options: TextFieldOptions
    private let hint: Text?
    private let supportingText: String?
    private let isError: Bool
    private let onSubmit: () -> Void

    init(
        label: LocalizedStringResource, model: TextFieldModel, state: KotlinTextFieldState, layout: Layout = .stacked,
        options: TextFieldOptions = TextFieldOptions(), hint: Text? = nil, supportingText: String? = nil, isError: Bool = false,
        onSubmit: @escaping () -> Void = {}
    ) {
        self.init(
            labelText: Text(label), model: model, state: state, layout: layout, options: options, hint: hint,
            supportingText: supportingText, isError: isError, onSubmit: onSubmit)
    }

    /// A field whose label Kotlin chooses (the starting distance's, by the athlete's units).
    init(
        label: any AppText, model: TextFieldModel, state: KotlinTextFieldState, layout: Layout = .stacked,
        options: TextFieldOptions = TextFieldOptions(), hint: Text? = nil, supportingText: String? = nil, isError: Bool = false,
        onSubmit: @escaping () -> Void = {}
    ) {
        self.init(
            labelText: Text(label), model: model, state: state, layout: layout, options: options, hint: hint,
            supportingText: supportingText, isError: isError, onSubmit: onSubmit)
    }

    private init(
        labelText: Text, model: TextFieldModel, state: KotlinTextFieldState, layout: Layout, options: TextFieldOptions, hint: Text?,
        supportingText: String?, isError: Bool, onSubmit: @escaping () -> Void
    ) {
        label = labelText
        self.hint = hint
        self.model = model
        self.state = state
        self.layout = layout
        self.options = options
        self.supportingText = supportingText
        self.isError = isError
        self.onSubmit = onSubmit
    }

    var body: some View {
        // Read here so a refused edit redraws the field, which otherwise keeps showing what was typed.
        let _ = model.refusals
        VStack(alignment: .leading, spacing: 4) {
            switch layout {
            case .stacked:
                caption
                field(axis: .horizontal)
                    // Whatever the screen around it does: a field's text starts at its leading edge.
                    .multilineTextAlignment(.leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 11)
                    .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))
                    .overlay {
                        if isError { RoundedRectangle(cornerRadius: 10).strokeBorder(.red, lineWidth: 1) }
                    }
            case .row:
                HStack(spacing: 12) {
                    label
                        .foregroundStyle(isError ? AnyShapeStyle(.red) : AnyShapeStyle(.primary))
                        .fixedSize()
                        .accessibilityHidden(true)
                    field(axis: .horizontal).multilineTextAlignment(.trailing)
                }
            case .multiline:
                caption
                field(axis: .vertical).lineLimit(3...12).multilineTextAlignment(.leading)
            }
            if let supportingText {
                Text(supportingText)
                    .font(.caption)
                    .foregroundStyle(isError ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                    .padding(.leading, layout == .stacked ? 4 : 0)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
            }
        }
        .task(id: ObjectIdentifier(state)) {
            model.bind(to: state)
            await model.follow(state.values.asAsyncSequence())
        }
    }

    private var caption: some View {
        label
            .font(.caption)
            .foregroundStyle(isError ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
            .padding(.leading, layout == .stacked ? 4 : 0)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityHidden(true)
    }

    private func field(axis: Axis) -> some View {
        TextField(text: Binding(get: { model.text }, set: { model.edit($0) }), prompt: hint ?? Text(verbatim: ""), axis: axis) { label }
            .keyboardType(options.keyboard)
            .textInputAutocapitalization(options.capitalization)
            .autocorrectionDisabled(!options.autocorrection)
            .textContentType(options.contentType)
            .submitLabel(options.submitLabel)
            .onSubmit(onSubmit)
    }
}
