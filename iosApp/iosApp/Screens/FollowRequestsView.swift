import SwiftUI

/// The athlete's follow requests, when they approve followers (graph/follow-requests), drawn from the states `FollowRequestsPresenter`
/// publishes: a choice of Requests and Declined, each account with its picture, name and handle, opening its profile, with Decline and
/// Approve (Approve alone once declined), and the notes and notices the shared page shows. Which accounts a tab lists, when an account can
/// be answered and what the page says are Kotlin's (`FollowRequestsState`, `FollowRequestsTexts`, `SignInAgainNotice`), shared with the
/// Compose page. It is pushed over the athlete's own profile.
struct FollowRequestsView: View {
    let state: FollowRequestsState

    var body: some View {
        // The accounts are read from the state's typed `accounts`, never from the content's erased value.
        ContentStateView(state.content, errorKind: .LoadFollowRequests, retry: { send(FollowRequestsEvent.Retry.shared) }) {
            list(state.accounts.elements(as: ProfileViewBasic.self))
        }
        .pageTitle(Text(.followRequestsTitle))
    }

    private func list(_ accounts: [ProfileViewBasic]) -> some View {
        List {
            tabs.listStatusRow()
            RefreshStatusView(state: state.content, errorKind: .LoadFollowRequests) { send(FollowRequestsEvent.Retry.shared) }
                .listStatusRow()
            if let notice = state.notice {
                noticeRow(notice).listStatusRow()
            }
            if accounts.isEmpty {
                Text(FollowRequestsTexts.shared.empty(tab: state.tab)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .listStatusRow()
            } else {
                Section {
                    ForEach(accounts, id: \.did.value) { account in row(account) }
                } footer: {
                    // A list explains itself under its rows on iOS; the two notes are never shown together, one being each tab's.
                    if state.showsDeclinedNote {
                        Text(.followRequestsDeclinedHelp)
                    } else if state.showsTruncatedNote {
                        Text(.followRequestsTruncated)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// Requests or Declined, in the order Kotlin declares them.
    private var tabs: some View {
        Picker(
            selection: Binding(get: { state.tab }, set: { send(FollowRequestsEvent.SelectTab(tab: $0)) })
        ) {
            ForEach(FollowRequestsTab.allCases, id: \.self) { tab in
                Text(FollowRequestsTexts.shared.tab(tab: tab)).tag(tab)
            }
        } label: {
            Text(.followRequestsTitle)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }

    /// Why the last answer failed, or that the session must sign in again to decline, with Sign in again where that helps.
    private func noticeRow(_ notice: SignInAgainNotice) -> some View {
        VStack(spacing: 8) {
            ErrorMessage(notice.error, layout: .row)
            if notice.offersSignInAgain {
                AppSecondaryButton(.actionSignInAgain) { send(FollowRequestsEvent.SignInAgain.shared) }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func row(_ account: ProfileViewBasic) -> some View {
        let name = AppPackage.getShownName(account)
        let enabled = state.canAnswer(account: account)
        return AppItemRow(
            Text(verbatim: name),
            lines: [Text(LabelText.shared.handle(handle: account.handle.value))],
            opens: { send(FollowRequestsEvent.Open(did: account.did)) }
        ) {
            Avatar(url: account.avatar, name: name)
        } trailing: {
            HStack(spacing: 8) {
                if state.offersDecline {
                    AppSecondaryButton(.followRequestsDecline, small: true) { send(FollowRequestsEvent.Decline(did: account.did)) }
                }
                AppButton(.followRequestsApprove, small: true) { send(FollowRequestsEvent.Approve(did: account.did)) }
            }
            .disabled(!enabled)
        }
    }

    private func send(_ event: any FollowRequestsEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Follow requests") {
        SamplePages(Samples.followRequests) { state in NavigationStack { FollowRequestsView(state: state) } }
    }

    #Preview("Declined, answering and notes, beyond loading") {
        SamplePages(Samples.followRequestsVariants) { state in NavigationStack { FollowRequestsView(state: state) } }
    }
#endif
