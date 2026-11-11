import Testing

@testable import ActivityTracker

/// What the native report and place-name sheets say, read from Swift on the shared samples (dev/shared-display-text): the same words the
/// shared sheets resolve, and their conditions.
@MainActor
struct ReportSheetTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    @Test func theReportSheetNamesTheAuthorAndListsEveryReason() throws {
        let state = try #require(Samples.report["loaded"])
        #expect(read(ReportTexts.shared.title(authorName: state.authorName)) == "Report Bob")
        let reasons = ReportTexts.shared.reasons.elements(as: ReportReasonChoice.self)
        #expect(
            reasons.map { read($0.label) } == [
                "Spam", "Sexual content", "Violence or graphic content", "Harassment or rude behavior", "Misleading or impersonation",
                "Other",
            ])
        #expect(reasons.allSatisfy { !state.chose(choice: $0) })
        #expect(!state.canSend)
    }

    @Test func otherAsksForTheNote() throws {
        let other = try #require(Samples.report["other"])
        #expect(other.noteRequired)
        #expect(read(ReportTexts.shared.note(state: other)) == "Note (say what's wrong)")
        #expect(!other.canSend)
        let chosen = try #require(Samples.report["chosen"])
        #expect(read(ReportTexts.shared.note(state: chosen)) == "Note (optional)")
        #expect(chosen.canSend)
        #expect(ReportTexts.shared.noteLimit == 1900)
    }

    @Test func sentOffersBlockByName() throws {
        let sent = try #require(Samples.report["sent"])
        #expect(sent.offerBlock)
        #expect(read(ReportTexts.shared.blockOffer(authorName: sent.authorName)) == "Block Bob")
        #expect(try #require(Samples.report["sentNoBlock"]).offerBlock == false)
    }

    @Test func theNameSheetSaysWhatProposingOrEditingDoes() throws {
        let propose = try #require(Samples.proposePlaceName["loaded"])
        #expect(read(ProposePlaceNameTexts.shared.title(state: propose)) == "Propose a name")
        #expect(read(ProposePlaceNameTexts.shared.confirm(state: propose)) == "Send")
        let edit = try #require(Samples.proposePlaceName["edit"])
        #expect(read(ProposePlaceNameTexts.shared.title(state: edit)) == "Edit your proposal")
        #expect(read(ProposePlaceNameTexts.shared.confirm(state: edit)) == "Save")
        #expect(try #require(Samples.proposePlaceName["invalid"]).showsInvalid)
        #expect(!propose.showsInvalid)
    }

    @Test func aPublicOnlyShareSaysWhy() throws {
        let limited = try #require(Samples.proposePlaceName["sharingPublicOnly"])
        #expect(
            limited.audiences.elements(as: String.self).map { read(ProposePlaceNameTexts.shared.audience(visibility: $0)) } == ["Public"])
        #expect(
            ProposePlaceNameTexts.shared.shareLimit(state: limited).map(read)
                == "Sign in again to share with only your followers or mutuals.")
        let sharing = try #require(Samples.proposePlaceName["sharing"])
        #expect(ProposePlaceNameTexts.shared.shareLimit(state: sharing) == nil)
        #expect(read(ProposePlaceNameTexts.shared.shareTextTooLong) == "Keep it to 1000 characters or fewer.")
    }
}
