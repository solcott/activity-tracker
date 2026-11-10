import Testing

@testable import ActivityTracker

/// A Swift field over a text a presenter owns (dev/ios-swift-interop): what the field writes, what comes back, and what it refuses.
@MainActor
struct TextFieldModelTests {
    /// Stands in for the Kotlin state, counting writes. Like it, a write is what `values` would then report.
    private final class FakeText: FieldText {
        private(set) var writes = 0
        private var held: String

        init(_ text: String = "") {
            held = text
        }

        var value: String {
            get { held }
            set {
                writes += 1
                held = newValue
            }
        }

        /// What a presenter does: changes the text without the field.
        func presenterSets(_ text: String) {
            held = text
        }
    }

    @Test func showsTheTextTheStateHoldsWhenMade() {
        #expect(TextFieldModel(FakeText("alice.test")).text == "alice.test")
    }

    @Test func anEditIsWrittenToTheState() {
        let state = FakeText()
        let model = TextFieldModel(state)

        model.edit("alice")

        #expect(model.text == "alice")
        #expect(state.value == "alice")
        #expect(state.writes == 1)
    }

    @Test func aClearByThePresenterReachesTheModel() async {
        let state = FakeText("alice.test")
        let model = TextFieldModel(state)
        let (changes, continuation) = AsyncStream<String>.makeStream()
        let following = Task { await model.follow(changes) }

        state.presenterSets("")
        continuation.yield("")
        continuation.finish()
        await following.value

        #expect(model.text == "")
    }

    @Test func typedTextComingBackIsNotWrittenAgain() async {
        let state = FakeText()
        let model = TextFieldModel(state)
        let (changes, continuation) = AsyncStream<String>.makeStream()
        let following = Task { await model.follow(changes) }

        model.edit("a")
        continuation.yield("a")
        continuation.finish()
        await following.value

        #expect(model.text == "a")
        #expect(state.writes == 1)
    }

    @Test func aLateReportOfAnOlderKeystrokeDoesNotUndoWhatWasTypedSince() async {
        let state = FakeText()
        let model = TextFieldModel(state)
        let (changes, continuation) = AsyncStream<String>.makeStream()
        let following = Task { await model.follow(changes) }

        model.edit("a")
        model.edit("ab")
        continuation.yield("a")
        continuation.finish()
        await following.value

        #expect(model.text == "ab")
        #expect(state.value == "ab")
    }

    @Test func aReplacedStateRebinds() {
        let first = FakeText("alice.test")
        let second = FakeText("")
        let model = TextFieldModel(first)

        model.bind(to: second)
        model.edit("bob")

        #expect(model.text == "bob")
        #expect(second.value == "bob")
        #expect(first.value == "alice.test")
    }

    @Test func bindingTheSameStateChangesNothing() {
        let state = FakeText("alice.test")
        let model = TextFieldModel(state)

        model.bind(to: state)

        #expect(model.text == "alice.test")
        #expect(state.writes == 0)
    }

    @Test func aNonDigitEditLeavesTheTextAsItWas() {
        let state = FakeText("12")
        let model = TextFieldModel(state, rules: TextFieldRules(digitsOnly: true))

        model.edit("12a")

        #expect(model.text == "12")
        #expect(state.writes == 0)
        #expect(model.refusals == 1)

        model.edit("123")
        #expect(model.text == "123")
    }

    @Test func anOverLengthEditLeavesTheTextAsItWas() {
        let state = FakeText("abc")
        let model = TextFieldModel(state, rules: TextFieldRules(maxLength: 3))

        model.edit("abcd")

        #expect(model.text == "abc")
        #expect(state.writes == 0)

        model.edit("ab")
        #expect(model.text == "ab")
    }

    @Test func lengthCountsWhatTheAthleteSees() {
        // One flag is one character, however many code points it takes.
        #expect(TextFieldRules(maxLength: 1).accepts("🇳🇿"))
        #expect(!TextFieldRules(maxLength: 1).accepts("ab"))
    }

    @Test func digitsOnlyTakesNoOtherNumberCharacters() {
        let rules = TextFieldRules(digitsOnly: true)
        #expect(rules.accepts("2026"))
        #expect(rules.accepts(""))
        #expect(!rules.accepts("٣"))
        #expect(!rules.accepts("1.5"))
    }

    @Test func theKotlinStateIsAFieldText() {
        let state = TextFieldPackage.textFieldStateOf(initialText: "alice.test")
        let model = TextFieldModel(state)

        model.edit("bob.test")

        #expect(model.text == "bob.test")
        #expect(state.value == "bob.test")
    }
}
