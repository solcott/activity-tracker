import SwiftUI

/// Who follows an athlete, or who they follow (graph/follow-requests), drawn from the states `FollowListPresenter` publishes, a page at a
/// time: each account with its picture, name and handle, opening its profile, and Remove on the athlete's own followers while they
/// approve followers. The words, the load error and when a removal is in flight are Kotlin's (`FollowListTexts`, `FollowListState`),
/// shared with the Compose page. It is pushed over the profile it was opened from.
struct FollowListView: View {
    let state: FollowListState

    var body: some View {
        PagedList(
            state.accounts, of: ProfileViewBasic.self, errorKind: state.loadErrorKind, id: { $0.did.value },
            retry: { send(FollowListEvent.Retry.shared) }, loadMore: { send(FollowListEvent.LoadMore.shared) }
        ) {
            // Why the last removal failed, over the accounts, which stay.
            if let error = state.actionError {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
        } row: { account in
            row(account)
        } empty: {
            Text(FollowListTexts.shared.empty(kind: state.kind)).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .pageTitle(
            Text(FollowListTexts.shared.title(kind: state.kind)),
            subtitle: FollowListTexts.shared.subtitle(subject: state.subject).map { Text($0) })
    }

    @ViewBuilder private func row(_ account: ProfileViewBasic) -> some View {
        let name = AppPackage.getShownName(account)
        let handle = Text(LabelText.shared.handle(handle: account.handle.value))
        let open = { send(FollowListEvent.Open(did: account.did)) }
        if state.canRemove {
            AppItemRow(Text(verbatim: name), lines: [handle], opens: open) {
                Avatar(url: account.avatar, name: name)
            } trailing: {
                AppSecondaryButton(.followListRemove, small: true) { send(FollowListEvent.Remove(did: account.did)) }
                    .disabled(state.isRemoving(account: account))
            }
        } else {
            // A row that opens a profile and has no control of its own carries the disclosure mark.
            AppItemRow(Text(verbatim: name), lines: [handle], opens: open) {
                Avatar(url: account.avatar, name: name)
            }
        }
    }

    private func send(_ event: any FollowListEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Followers") {
        SamplePages(Samples.followList) { state in NavigationStack { FollowListView(state: state) } }
    }

    #Preview("Following and removing, beyond loading") {
        SamplePages(Samples.followListVariants) { state in NavigationStack { FollowListView(state: state) } }
    }
#endif
