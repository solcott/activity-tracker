import Testing

@testable import ActivityTracker

/// What the follow lists and follow requests say and offer, read from Swift (graph/follow-requests, dev/shared-display-text,
/// dev/ios-swift-interop).
@MainActor
struct FollowListsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private func accounts(_ state: FollowListState) -> [ProfileViewBasic] {
        state.accounts.items.compactMap { $0 as? ProfileViewBasic }
    }

    private func handles(_ accounts: [ProfileViewBasic]) -> [String?] {
        accounts.map { read(LabelText.shared.handle(handle: $0.handle.value)) }
    }

    @Test func alicesFollowers() throws {
        let state = try #require(Samples.followList["loaded"])
        #expect(read(FollowListTexts.shared.title(kind: state.kind)) == "Followers")
        #expect(read(FollowListTexts.shared.subtitle(subject: state.subject)) == "@alice.test")
        #expect(accounts(state).map { AppPackage.getShownName($0) } == ["Bob", "Carol"])
        #expect(handles(accounts(state)) == ["@bob.test", "@carol.test"])
        #expect(state.loadErrorKind == .LoadFollowers)
        #expect(!state.canRemove)
    }

    @Test func beforeTheFirstPageNoHandleShows() throws {
        #expect(try #require(Samples.followList["loading"]).subject == nil)
        #expect(read(FollowListTexts.shared.subtitle(subject: try #require(Samples.followList["loading"]).subject)) == nil)
        #expect(read(FollowListTexts.shared.empty(kind: try #require(Samples.followList["empty"]).kind)) == "No followers yet.")
    }

    @Test func bobsFollowing() throws {
        let state = try #require(Samples.followListVariants["following"])
        #expect(read(FollowListTexts.shared.title(kind: state.kind)) == "Following")
        #expect(read(FollowListTexts.shared.subtitle(subject: state.subject)) == "@bob.test")
        #expect(handles(accounts(state)) == ["@alice.test", "@carol.test"])
        #expect(state.loadErrorKind == .LoadFollowing)
        let empty = try #require(Samples.followListVariants["followingEmpty"])
        #expect(read(FollowListTexts.shared.empty(kind: empty.kind)) == "Not following anyone yet.")
    }

    @Test func aRemovalInFlightAndAFailedOne() throws {
        let state = try #require(Samples.followListVariants["removable"])
        #expect(state.canRemove)
        #expect(accounts(state).map { state.isRemoving(account: $0) } == [true, false])
        #expect(state.actionError.map { AppStrings.string(ErrorTexts.shared.of(error: $0)) }?.contains("remove the follower") == true)
        #expect(state.actionError.map { AppStrings.string(ErrorTexts.shared.of(error: $0)) }?.contains("reach the server") == true)
    }

    @Test func twoRequests() throws {
        let state = try #require(Samples.followRequests["loaded"])
        #expect(state.tab == .Requests)
        #expect(handles(state.accounts.elements(as: ProfileViewBasic.self)) == ["@bob.test", "@carol.test"])
        #expect(state.offersDecline)
        #expect(state.accounts.elements(as: ProfileViewBasic.self).allSatisfy { state.canAnswer(account: $0) })
        #expect(!state.showsDeclinedNote && !state.showsTruncatedNote)
        #expect(state.notice == nil)
        #expect(FollowRequestsTab.allCases.map { read(FollowRequestsTexts.shared.tab(tab: $0)) } == ["Requests", "Declined"])
    }

    @Test func theDeclinedTab() throws {
        let state = try #require(Samples.followRequestsVariants["declined"])
        #expect(handles(state.accounts.elements(as: ProfileViewBasic.self)) == ["@dave.test"])
        #expect(!state.offersDecline)
        #expect(state.showsDeclinedNote)
        let empty = try #require(Samples.followRequestsVariants["declinedEmpty"])
        #expect(empty.accounts.elements(as: ProfileViewBasic.self).isEmpty)
        #expect(!empty.showsDeclinedNote)
        #expect(read(FollowRequestsTexts.shared.empty(tab: empty.tab)) == "No declined requests.")
    }

    @Test func noRequests() throws {
        let state = try #require(Samples.followRequests["empty"])
        #expect(state.accounts.elements(as: ProfileViewBasic.self).isEmpty)
        #expect(read(FollowRequestsTexts.shared.empty(tab: state.tab)) == "No follow requests.")
        // Before the first answer the typed getter reads the empty default too, so the view never needs the erased value.
        #expect(try #require(Samples.followRequests["loading"]).accounts.elements(as: ProfileViewBasic.self).isEmpty)
    }

    @Test func anAnswerInFlightAnUnanswerableAccountAndAFailure() throws {
        let state = try #require(Samples.followRequestsVariants["answering"])
        let accounts = state.accounts.elements(as: ProfileViewBasic.self)
        #expect(accounts.map { state.canAnswer(account: $0) } == [false, true, false])
        let notice = try #require(state.notice)
        #expect(!notice.offersSignInAgain)
        #expect(AppStrings.string(ErrorTexts.shared.of(error: notice.error)).contains("answer the follow request"))
    }

    @Test func signInAgainToDecline() throws {
        let notice = try #require(Samples.followRequestsVariants["needsSignIn"]?.notice)
        #expect(notice.offersSignInAgain)
        #expect(AppStrings.string(ErrorTexts.shared.of(error: notice.error)).hasPrefix("Sign in again to share activities"))
    }

    @Test func moreRequestsThanWereRead() throws {
        #expect(try #require(Samples.followRequestsVariants["truncated"]).showsTruncatedNote)
    }
}
