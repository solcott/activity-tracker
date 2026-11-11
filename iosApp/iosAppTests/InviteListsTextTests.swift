import Testing

@testable import ActivityTracker

/// What the invites list and Add people say and offer, read from Swift (feed/activity-invites, dev/shared-display-text,
/// dev/ios-swift-interop).
@MainActor
struct InviteListsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private func invites(_ state: ActivityInvitesState) -> [InviteView] { state.invites.elements(as: InviteView.self) }

    private func titles(_ state: ActivityInvitesState) -> [String?] { invites(state).map { read(ActivityInvitesTexts.shared.title(invite: $0)) } }

    private func error(_ notice: SignInAgainNotice?) -> String? { notice.map { AppStrings.string(ErrorTexts.shared.of(error: $0.error)) } }

    @Test func twoInvites() {
        let state = Samples.activityInvites["loaded"]!
        #expect(state.tab == .Pending)
        #expect(read(ActivityInvitesTexts.shared.tab(tab: .Pending)) == "Invites")
        #expect(titles(state) == ["Gravel loop", "Ride"])
        #expect(invites(state).map { read(ActivityInvitesTexts.shared.from(invite: $0)) } == ["Bob added you", "Carol added you"])
        #expect(read(ActivityInvitesTexts.shared.summary(invite: invites(state)[0], units: .Metric)) == "32.40 km · 1:05:00")
        #expect(read(ActivityInvitesTexts.shared.summary(invite: invites(state)[0], units: .Imperial)) == "20.13 mi · 1:05:00")
        #expect(state.offersDecline)
        #expect(invites(state).allSatisfy { state.canAnswer(invite: $0) })
        #expect(!state.showsDeclinedNote)
        #expect(state.notice == nil)
    }

    @Test func noInvites() {
        let state = Samples.activityInvites["empty"]!
        #expect(invites(state).isEmpty)
        #expect(read(ActivityInvitesTexts.shared.empty(tab: .Pending)) == "No invites")
        #expect(read(ActivityInvitesTexts.shared.empty(tab: .Declined)) == "No declined invites")
        #expect(Samples.activityInvites["loading"]!.invites.elements(as: InviteView.self).isEmpty)
    }

    @Test func theDeclinedTab() {
        let state = Samples.activityInvitesVariants["declined"]!
        #expect(read(ActivityInvitesTexts.shared.tab(tab: state.tab)) == "Declined")
        #expect(titles(state) == ["Gravel loop"])
        #expect(!state.offersDecline)
        #expect(state.showsDeclinedNote)
        let empty = Samples.activityInvitesVariants["declinedEmpty"]!
        #expect(invites(empty).isEmpty && !empty.showsDeclinedNote)
    }

    @Test func aDeclineInFlightAndAFailedOne() {
        let state = Samples.activityInvitesVariants["answering"]!
        #expect(invites(state).map { state.canAnswer(invite: $0) } == [false, true])
        #expect(error(state.notice)?.contains("reach the server") == true)
        #expect(state.notice?.offersSignInAgain == false)
    }

    @Test func signInAgainToDecline() {
        let state = Samples.activityInvitesVariants["needsSignIn"]!
        #expect(!state.offersDecline)
        #expect(state.notice?.offersSignInAgain == true)
        #expect(error(state.notice)?.hasPrefix("Sign in again") == true)
    }

    @Test func anInviteWithoutADistanceShowsItsTimeAlone() {
        let state = Samples.activityInvitesVariants["noDistance"]!
        #expect(titles(state) == ["Trainer ride"])
        #expect(read(ActivityInvitesTexts.shared.summary(invite: invites(state)[0], units: .Metric)) == "1:05:00")
    }

    private func invitees(_ state: AddPeopleState) -> [InviteeView] { state.invitees.elements(as: InviteeView.self) }

    private func followers(_ state: AddPeopleState) -> [ProfileViewBasic] { state.followers.elements(as: ProfileViewBasic.self) }

    @Test func oneJoinedOneToAdd() {
        let state = Samples.addPeople["loaded"]!
        #expect(invitees(state).map { AppPackage.getShownName($0.invitee) } == ["Bob"])
        #expect(invitees(state).map { read(AddPeopleTexts.shared.status(invitee: $0)) } == ["Joined"])
        #expect(invitees(state).map { read(AddPeopleTexts.shared.remove(invitee: $0)) } == ["Remove"])
        #expect(followers(state).map { AppPackage.getShownName($0) } == ["Carol"])
        #expect(invitees(state).allSatisfy { state.canRemove(invitee: $0) })
        #expect(followers(state).allSatisfy { state.canAdd(follower: $0) })
        #expect(!state.full && state.notice == nil)
    }

    @Test func aPendingInviteeAndAnAddInFlight() {
        let state = Samples.addPeopleVariants["pending"]!
        #expect(invitees(state).map { read(AddPeopleTexts.shared.status(invitee: $0)) } == ["Pending"])
        #expect(invitees(state).map { read(AddPeopleTexts.shared.remove(invitee: $0)) } == ["Cancel"])
        #expect(followers(state).map { state.canAdd(follower: $0) } == [false])
        #expect(error(state.notice)?.contains("reach the server") == true)
        let removing = Samples.addPeopleVariants["removing"]!
        #expect(invitees(removing).map { removing.canRemove(invitee: $0) } == [false])
    }

    @Test func aFullActivity() {
        let state = Samples.addPeopleVariants["full"]!
        #expect(invitees(state).count == 20)
        #expect(state.full)
        #expect(followers(state).map { state.canAdd(follower: $0) } == [false])
        #expect(invitees(state).allSatisfy { state.canRemove(invitee: $0) })
    }

    @Test func noFollowersAndASessionThatCantInvite() {
        #expect(followers(Samples.addPeopleVariants["noFollowers"]!).isEmpty)
        let state = Samples.addPeopleVariants["needsSignIn"]!
        #expect(state.notice?.offersSignInAgain == true)
        #expect(!followers(state).contains { state.canAdd(follower: $0) })
        #expect(!invitees(state).contains { state.canRemove(invitee: $0) })
        #expect(Samples.addPeople["failed"]!.invitees.elements(as: InviteeView.self).isEmpty)
    }

    @Test func theEventsCarryWhatSwiftHolds() {
        let invite = invites(Samples.activityInvites["loaded"]!)[0]
        #expect(ActivityInvitesEvent.Open(invite: invite.uri).invite.value == invite.uri.value)
        let invitee = invitees(Samples.addPeople["loaded"]!)[0]
        #expect(AddPeopleEvent.Remove(invitee: invitee).invitee.invitee.did.value == "did:plc:bob")
    }
}
