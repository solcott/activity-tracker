import Foundation
import Testing

@testable import ActivityTracker

/// What the Moderation page says and offers (moderation/private-subjects, dev/shared-display-text). The rules are Kotlin's, with their
/// own tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared page does.
@MainActor
struct ModerationTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    private func comment(_ sample: String) throws -> CommentView {
        let state = try #require(Samples.moderationVariants[sample])
        return try #require(state.subject as? ModerationSubject.Comment).comment
    }

    @Test func theVisibilityLineNamesTheTier() {
        let texts = ModerationSubjectTexts.shared
        #expect(read(texts.visibility(visibility: "followers")) == "Visible to: Followers")
        #expect(read(texts.visibility(visibility: "public")) == "Visible to: Public")
        #expect(read(texts.visibility(visibility: "friends-of-friends")) == "Visible to: Only me")
    }

    @Test func aCommentsAuthorIsTheirNameElseTheirHandle() throws {
        let texts = ModerationSubjectTexts.shared
        #expect(read(texts.commentAuthor(comment: try comment("comment"))) == "Bob")
        #expect(read(texts.commentAuthor(comment: try comment("commentNoName"))) == "@carol.test")
    }

    @Test func openIsHeldWhileOpening() throws {
        let texts = ModerationSubjectTexts.shared
        #expect(texts.canOpen(state: try #require(Samples.moderation["loaded"])))
        #expect(!texts.canOpen(state: try #require(Samples.moderationVariants["opening"])))
    }
}
