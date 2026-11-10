import SwiftUI

/// The athlete's blocked or muted accounts (ui/settings-layout), drawn from the states `BlockListPresenter` publishes, a page at a time:
/// each account with its picture, name and handle, and Unblock or Unmute where this app can undo it. The words, the load error and when
/// an account's change is in flight are Kotlin's (`BlockListTexts`, `BlockListState`), shared with the Compose page. A profile opened
/// from it opens full width over Settings.
struct BlockListView: View {
    let state: BlockListState

    var body: some View {
        PagedList(
            state.accounts, of: BlockListAccount.self, errorKind: state.loadErrorKind, id: { $0.profile.did.value },
            retry: { send(BlockListEvent.Retry.shared) }, loadMore: { send(BlockListEvent.LoadMore.shared) }
        ) {
            // Why the last unblock or unmute failed, over the accounts, which stay.
            if let error = state.actionError {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
        } row: { account in
            row(account)
        } empty: {
            Text(BlockListTexts.shared.empty(kind: state.kind)).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .pageTitle(Text(BlockListTexts.shared.title(kind: state.kind)))
    }

    private func row(_ account: BlockListAccount) -> some View {
        let profile = account.profile
        let name = AppPackage.getShownName(profile)
        let note = BlockListTexts.shared.note(account: account).map { Text($0) }
        return AppItemRow(
            Text(verbatim: name),
            lines: [Text(LabelText.shared.handle(handle: profile.handle.value))] + (note.map { [$0] } ?? []),
            opens: { send(BlockListEvent.Open(did: profile.did)) }
        ) {
            Avatar(url: profile.avatar, name: name)
        } trailing: {
            if account.removable {
                AppSecondaryButton(BlockListTexts.shared.remove(kind: state.kind), small: true) {
                    send(BlockListEvent.Remove(did: profile.did))
                }
                .disabled(state.isRemoving(account: account))
            }
        }
    }

    private func send(_ event: any BlockListEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Blocked accounts") {
        SamplePages(Samples.blockList) { state in NavigationStack { BlockListView(state: state) } }
    }

    #Preview("Blocked and muted, beyond loading") {
        SamplePages(Samples.blockListVariants) { state in NavigationStack { BlockListView(state: state) } }
    }

    #Preview("Blocked accounts beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(SettingsRows.blocked))
                },
                page: BlockListView(state: Samples.blockList["loaded"]!).environment(\.inPane, true))
        }
    }
#endif
