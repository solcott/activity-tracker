import Observation

/// A text a field edits and something else may change: the app's Kotlin `TextFieldState`, which a screen's presenter owns and can seed,
/// clear or replace (dev/ios-swift-interop). Tests use a Swift one.
protocol FieldText: AnyObject {
    var value: String { get set }
}

extension KotlinTextFieldState: FieldText {}

/// What a field takes of an edit (ui/ios-design-system). A digits-only field keeps an edit's digits and drops the rest, so a digit typed
/// right after a refused character is kept with the edit that carries both; an edit longer than the maximum, digits or not, leaves the
/// text as it was, since cutting it short would make another value.
struct TextFieldRules: Equatable {
    /// Only the digits 0 to 9, as a count or a year takes.
    var digitsOnly = false

    /// The most characters the field holds, counted as the athlete sees them.
    var maxLength: Int?

    /// What the field keeps of `text`: the text itself, its digits alone for a digits-only field, or nil when the edit is refused.
    func taken(_ text: String) -> String? {
        let kept = digitsOnly ? String(text.filter { $0.isASCII && $0.isNumber }) : text
        if let maxLength, kept.count > maxLength { return nil }
        return kept
    }

    /// Whether `text` is taken as it is.
    func accepts(_ text: String) -> Bool { taken(text) == text }
}

/// The text a SwiftUI field shows, kept the same as a Kotlin text field state in both directions.
///
/// The field writes through `edit`, which writes the state; the state's changes come back through `follow`. Every change the state
/// reports is answered by reading the state as it is now, never by taking the reported text: a report can be a keystroke old by the
/// time it arrives, and showing it would undo what was typed since. What the field wrote is what the state holds, so it changes nothing.
@Observable
final class TextFieldModel {
    private(set) var text: String

    /// Counts edits the rules refused or cut. A view reads it so that one, which leaves `text` other than what was typed, still redraws the
    /// field over it.
    private(set) var refusals = 0

    var rules: TextFieldRules

    @ObservationIgnored private var state: any FieldText

    init(_ state: any FieldText, rules: TextFieldRules = TextFieldRules()) {
        self.state = state
        self.rules = rules
        text = state.value
    }

    /// The field's edit: taken and written to the state, what the rules keep of it, or refused. Whenever the rules drop or refuse
    /// something the field shows, it counts a refusal, so the field is redrawn over what was typed.
    func edit(_ edited: String) {
        guard edited != text else { return }
        guard let taken = rules.taken(edited) else {
            refusals += 1
            return
        }
        if taken != edited { refusals += 1 }
        guard taken != text else { return }
        text = taken
        state.value = taken
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
