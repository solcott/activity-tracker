import Testing

@testable import ActivityTracker

/// The feature labels the native views ask Kotlin for (dev/shared-display-text, "Feature labels are chosen once"), read from Swift through
/// the catalog: the words the shared UI resolves from the same functions.
@MainActor
struct FeatureLabelTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func saveReadsSavingWhileItRuns() throws {
        #expect(read(LabelText.shared.save(saving: false)) == "Save")
        #expect(read(LabelText.shared.save(saving: true)) == "Saving…")
        let saving = try #require(Samples.zones["saving"])
        #expect(read(LabelText.shared.save(saving: saving.saving)) == "Saving…")
    }

    @Test func eachTabIsNamedByKotlin() {
        #expect([TopLevelDestination.Feed, .Search, .You].map { read($0.title) } == ["Feed", "Search", "You"])
    }

    @Test func aSectionIsHeadedByItsTypeOrRetired() {
        #expect(read(GearListTexts.shared.section(type: "bike")) == read(GearTexts.shared.type(type: "bike")))
        #expect(read(GearListTexts.shared.section(type: nil)) == "Retired")
    }

    @Test func theVisibilityNoteIsTheHelpUntilTheTierIsLocked() throws {
        let open = try #require(Samples.activityDefaults["loaded"]).defaults
        let locked = try #require(Samples.activityDefaults["locked"]).defaults
        #expect(read(VisibilityTexts.shared.activityNote(fields: open)) == String(localized: .activityVisibilityHelp))
        #expect(read(VisibilityTexts.shared.activityNote(fields: locked)) == read(VisibilityTexts.shared.lockedNote(fields: locked)))
    }

    @Test func onlyDeleteAndBlockAskFirst() throws {
        let carol = AppText.Companion.shared.written(text: "Carol")
        let block = try #require(CommentsTexts.shared.confirmation(action: .BlockAuthor, author: carol))
        #expect(read(block.title) == "Block Carol?")
        #expect(read(block.confirm) == "Block")
        let delete = try #require(CommentsTexts.shared.confirmation(action: .Delete, author: carol))
        #expect(read(delete.confirm) == read(CommentsTexts.shared.label(action: .Delete)))
        #expect(CommentsTexts.shared.confirmation(action: .Hide, author: carol) == nil)
        #expect(CommentsTexts.shared.confirmation(action: .Report, author: carol) == nil)
    }

    @Test func aPickersNullChoiceIsNamed() {
        #expect(read(SegmentBrowseTexts.shared.sportChoice(sport: nil)) == "All")
        #expect(read(ImportActivityTexts.shared.deviceKindChoice(type: "bike", kind: nil)) == "None")
        #expect(read(ZoneTexts.shared.modelChoice(token: "someoneElses")) == "someoneElses")
    }

    @Test func theExportsButtonsFollowItsStatus() throws {
        let ready = try #require(Samples.account["exportReady"])
        let controls = AccountTexts.shared.exportControls(export: ready.export)
        #expect(controls.map { read($0.label) } == ["Save", "Delete export"])
        #expect(controls.map(\.primary) == [true, false])
        let leaving = try #require(Samples.leave["exportRunning"])
        #expect(LeaveTexts.shared.exportControls(state: leaving).map(\.action) == [.Cancel])
        let starting = try #require(Samples.leave["starting"])
        #expect(LeaveTexts.shared.exportControls(state: starting).map(\.enabled) == [false])
    }

    @Test func theAccountRowSaysItNeedsSigningIn() throws {
        let needs = try #require(Samples.settings["needsSignIn"])
        #expect(read(SettingsTexts.shared.accountSummary(state: needs)) == "Sign in again to use everything")
        let fine = try #require(Samples.settings["loaded"])
        #expect(SettingsTexts.shared.accountSummary(state: fine) == nil)
    }
}
