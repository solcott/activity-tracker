import Observation

/// A text a field edits and something else may change: the app's Kotlin `TextFieldState`, which a screen's presenter owns and can seed,
/// clear or replace (dev/ios-swift-interop). Tests use a Swift one.
protocol FieldText: AnyObject {
    var value: String { get set }
}

extension KotlinTextFieldState: FieldText {}

/// What a field refuses. An edit that breaks a rule leaves the text as it was, so a rule never rewrites what was typed.
struct TextFieldRules: Equatable {
    /// Only the digits 0 to 9, as a count or a year takes.
    var digitsOnly = false

    /// The most characters the field holds, counted as the athlete sees them.
    var maxLength: Int?

    func accepts(_ text: String) -> Bool {
        if digitsOnly, !text.allSatisfy({ $0.isASCII && $0.isNumber }) { return false }
        if let maxLength, text.count > maxLength { return false }
        return true
    }
}

/// The text a SwiftUI field shows, kept the same as a Kotlin text field state in both directions.
///
/// The field writes through `edit`, which writes the state; the state's changes come back through `follow`. Every change the state
/// reports is answered by reading the state as it is now, never by taking the reported text: a report can be a keystroke old by the
/// time it arrives, and showing it would undo what was typed since. What the field wrote is what the state holds, so it changes nothing.
@Observable
final class TextFieldModel {
    private(set) var text: String

    /// Counts refused edits. A view reads it so that a refusal, which leaves `text` as it was, still redraws the field over what was typed.
    private(set) var refusals = 0

    var rules: TextFieldRules

    @ObservationIgnored private var state: any FieldText

    init(_ state: any FieldText, rules: TextFieldRules = TextFieldRules()) {
        self.state = state
        self.rules = rules
        text = state.value
    }

    /// The field's edit: taken and written to the state, or refused by a rule.
    func edit(_ edited: String) {
        guard edited != text else { return }
        guard rules.accepts(edited) else {
            refusals += 1
            return
        }
        text = edited
        state.value = edited
    }

    /// Shows what the state holds now, when that isn't what the field shows: a presenter seeded, cleared or restored it.
    func refresh() {
        let held = state.value
        if held != text { text = held }
    }

    /// Edits and shows `state` from now on. A presenter can replace the state object, as sign-in does when a session ends; the same
    /// object changes nothing.
    func bind(to state: any FieldText) {
        guard state !== self.state else { return }
        self.state = state
        refresh()
    }

    /// Refreshes on each element of `changes` (the state's `values`), until it ends or the task this runs in is cancelled.
    func follow<Changes: AsyncSequence>(_ changes: Changes) async {
        do {
            for try await _ in changes {
                refresh()
            }
        } catch {
            // Cancelled, or the flow ended with an error; either way there is nothing more to follow.
        }
    }
}
