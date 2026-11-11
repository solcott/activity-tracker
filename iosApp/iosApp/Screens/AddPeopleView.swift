import SwiftUI

/// The people the athlete added to their own activity, and adding more of their followers (feed/activity-invites), drawn from the states
/// `AddPeoplePresenter` publishes: the note on what people added can see, each one added with their picture, name and handle, Pending
/// or Joined, and Cancel or Remove, then each follower not added yet with Add, and the notes and notices the shared page shows. What
/// each says and when its button can be used are Kotlin's (`AddPeopleState`, `AddPeopleTexts`, `SignInAgainNotice`), shared with the
/// Compose page. It is pushed over the athlete's own activity.
struct AddPeopleView: View {
    let state: AddPeopleState

    var body: some View {
        // The lists are read from the state's typed getters, never from the content's erased value.
        ContentStateView(state.content, errorKind: .LoadInvitees, retry: { send(AddPeopleEvent.Retry.shared) }) {
            list(invitees: state.invitees.elements(as: InviteeView.self), followers: state.followers.elements(as: ProfileViewBasic.self))
        }
        .pageTitle(Text(.addPeopleTitle))
    }

    private func list(invitees: [InviteeView], followers: [ProfileViewBasic]) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadInvitees) { send(AddPeopleEvent.Retry.shared) }
                .listStatusRow()
            // What adding someone shares, first, as the shared page puts it, so it reads before either list.
            Text(.addPeopleNotice).font(.footnote).foregroundStyle(.secondary).listStatusRow()
            if let notice = state.notice {
                noticeRow(notice).listStatusRow()
            }
            if !invitees.isEmpty {
                Section {
                    ForEach(invitees, id: \.invitee.did.value) { invitee in inviteeRow(invitee) }
                } header: {
                    Text(.addPeopleAdded)
                }
            }
            Section {
                if followers.isEmpty {
                    Text(.addPeopleNoFollowers).foregroundStyle(.secondary)
                }
                ForEach(followers, id: \.did.value) { follower in followerRow(follower) }
            } header: {
                Text(.addPeopleFollowers)
            } footer: {
                if state.full {
                    Text(.addPeopleFull)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// Why adding or removing failed, or that the session must sign in again to add people, with Sign in again where that helps.
    private func noticeRow(_ notice: SignInAgainNotice) -> some View {
        VStack(spacing: 8) {
            ErrorMessage(notice.error, layout: .row)
            if notice.offersSignInAgain {
                AppSecondaryButton(.actionSignInAgain) { send(AddPeopleEvent.SignInAgain.shared) }
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Someone added: Pending or Joined, before Cancel or Remove. The row opens nothing, as the shared one.
    private func inviteeRow(_ invitee: InviteeView) -> some View {
        let texts = AddPeopleTexts.shared
        return accountRow(invitee.invitee) {
            HStack(spacing: 8) {
                Text(texts.status(invitee: invitee)).font(.subheadline).foregroundStyle(.secondary)
                AppSecondaryButton(texts.remove(invitee: invitee), small: true) { send(AddPeopleEvent.Remove(invitee: invitee)) }
                    .disabled(!state.canRemove(invitee: invitee))
            }
        }
    }

    private func followerRow(_ follower: ProfileViewBasic) -> some View {
        accountRow(follower) {
            AppButton(.addPeopleAdd, small: true) { send(AddPeopleEvent.Add(did: follower.did)) }
                .disabled(!state.canAdd(follower: follower))
        }
    }

    private func accountRow<Trailing: View>(_ account: ProfileViewBasic, @ViewBuilder trailing: () -> Trailing) -> some View {
        let name = AppPackage.getShownName(account)
        return AppItemRow(Text(verbatim: name), lines: [Text(LabelText.shared.handle(handle: account.handle.value))]) {
            Avatar(url: account.avatar, name: name)
        } trailing: {
            trailing()
        }
    }

    private func send(_ event: any AddPeopleEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Add people") {
        SamplePages(Samples.addPeople) { state in NavigationStack { AddPeopleView(state: state) } }
    }

    #Preview("Pending, busy, full and notices, beyond loading") {
        SamplePages(Samples.addPeopleVariants) { state in NavigationStack { AddPeopleView(state: state) } }
    }
#endif
