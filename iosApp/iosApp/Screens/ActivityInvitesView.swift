import SwiftUI

/// The athlete's invites to other athletes' activities (feed/activity-invites), drawn from the states `ActivityInvitesPresenter`
/// publishes: a choice of Invites and Declined, each invite with the activity's title, who added the athlete and
/// its distance and time, with View, and Decline while it is pending and the session can decline, and the notes and notices the shared
/// page shows. Like the shared row, an invite has no picture: the text beside its two buttons has the room to keep its distance and time
/// on one line. Which invites a tab lists, when an invite can be answered and what the page says are Kotlin's (`ActivityInvitesState`,
/// `ActivityInvitesTexts`, `SignInAgainNotice`), shared with the Compose page. It is pushed over the athlete's own profile, and View
/// opens the shared accept page.
struct ActivityInvitesView: View {
    let state: ActivityInvitesState
    @Environment(\.stats) private var stats

    var body: some View {
        // The invites are read from the state's typed `invites`, never from the content's erased value.
        ContentStateView(state.content, errorKind: .LoadActivityInvites, retry: { send(ActivityInvitesEvent.Retry.shared) }) {
            list(state.invites.elements(as: InviteView.self))
        }
        .pageTitle(Text(.activityInvitesTitle))
    }

    private func list(_ invites: [InviteView]) -> some View {
        List {
            tabs.listStatusRow()
            RefreshStatusView(state: state.content, errorKind: .LoadActivityInvites) { send(ActivityInvitesEvent.Retry.shared) }
                .listStatusRow()
            if let notice = state.notice {
                noticeRow(notice).listStatusRow()
            }
            if invites.isEmpty {
                Text(ActivityInvitesTexts.shared.empty(tab: state.tab)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .listStatusRow()
            } else {
                Section {
                    ForEach(invites, id: \.uri.value) { invite in row(invite) }
                } footer: {
                    // A list explains itself under its rows on iOS.
                    if state.showsDeclinedNote {
                        Text(.activityInvitesDeclinedHelp)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// Invites or Declined, in the order Kotlin declares them.
    private var tabs: some View {
        Picker(
            selection: Binding(get: { state.tab }, set: { send(ActivityInvitesEvent.SelectTab(tab: $0)) })
        ) {
            ForEach(ActivityInvitesTab.allCases, id: \.self) { tab in
                Text(ActivityInvitesTexts.shared.tab(tab: tab)).tag(tab)
            }
        } label: {
            Text(.activityInvitesTitle)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }

    /// Why the last decline failed, or that the session must sign in again to decline, with Sign in again where that helps.
    private func noticeRow(_ notice: SignInAgainNotice) -> some View {
        VStack(spacing: 8) {
            ErrorMessage(notice.error, layout: .row)
            if notice.offersSignInAgain {
                AppSecondaryButton(.actionSignInAgain) { send(ActivityInvitesEvent.SignInAgain.shared) }
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// The activity's title, then who added the athlete and its distance and time; the row itself opens nothing, as the shared one.
    private func row(_ invite: InviteView) -> some View {
        let texts = ActivityInvitesTexts.shared
        return AppItemRow(
            Text(texts.title(invite: invite)),
            lines: [Text(texts.from(invite: invite)), Text(texts.summary(invite: invite, units: stats.units))]
        ) {
            EmptyView()
        } trailing: {
            HStack(spacing: 8) {
                if state.offersDecline {
                    AppSecondaryButton(.activityInvitesDecline, small: true) { send(ActivityInvitesEvent.Decline(invite: invite.uri)) }
                }
                AppButton(.activityInvitesView, small: true) { send(ActivityInvitesEvent.Open(invite: invite.uri)) }
            }
            .disabled(!state.canAnswer(invite: invite))
        }
    }

    private func send(_ event: any ActivityInvitesEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Activity invites") {
        SamplePages(Samples.activityInvites) { state in NavigationStack { ActivityInvitesView(state: state) } }
    }

    #Preview("Declined, declining and notices, beyond loading") {
        SamplePages(Samples.activityInvitesVariants) { state in NavigationStack { ActivityInvitesView(state: state) } }
    }
#endif
