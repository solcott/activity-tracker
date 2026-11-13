import SwiftUI
import UIKit

/// The Account page (auth/account-page, ui/settings-layout), drawn from the states `AccountPresenter` publishes: who is signed in and
/// where, whether the sign-in is up to date, where the account itself is managed, the data export (auth/data-export), Sign out, and
/// Leave Activity Tracker. What it says that follows a rule, and when it offers Sign in again, is Kotlin's (`AccountTexts`,
/// `offersSignInAgain`), shared with the Compose page. Ending the session with work pending asks first in an alert, as does deleting the
/// app's copy of an export after the share sheet.
///
/// Alone it is titled in the stack's bar; beside Settings' list, where it is always the first page, in its own header.
struct AccountView: View {
    let state: AccountState
    /// Set once Copy is tapped, until the page goes, as the shared page's button does.
    @State private var copied = false
    @State private var share = ArchiveShare()

    var body: some View {
        List {
            Section {
                AppListRow(Text(.accountHandle), summary: Text(verbatim: state.handle))
                AppListRow(Text(.accountDid), summary: Text(verbatim: state.did)) {
                    AppSecondaryButton(AccountTexts.shared.didCopy(copied: copied), small: true) {
                        UIPasteboard.general.string = state.did
                        copied = true
                    }
                }
                AppListRow(Text(.accountServer), summary: Text(verbatim: state.pdsHost))
            } header: {
                Text(.accountSectionIdentity)
            }
            Section {
                grant
            } header: {
                Text(.accountSectionSignIn)
            }
            Section {
                Text(.accountManagedByServer(state.pdsHost))
            } header: {
                Text(.accountSectionManaged)
            }
            Section {
                export
            } header: {
                Text(.accountSectionExport)
            }
            Section {
                Button(.profileSignOut) { state.eventSink(AccountEvent.SignOut.shared) }
                    .disabled(state.ending)
            }
            // Apart from Sign out and from the server's page, where the account itself is deleted (auth/leaving).
            Section {
                // Its summary is a sentence, which reads under the title rather than squeezed in beside it, as a row's value is.
                Button {
                    state.eventSink(AccountEvent.Leave.shared)
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(.leaveTitle)
                            Text(.accountLeaveSummary(state.handle)).font(.footnote).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .tint(.primary)
                .disabled(state.ending)
            } header: {
                Text(.accountSectionLeave)
            }
        }
        .listStyle(.insetGrouped)
        .pageTitle(Text(.accountTitle))
        .alert(
            Text(state.confirming.map { AccountTexts.shared.endTitle(ending: $0) } ?? TextPackage.AppText.Written(text: "")),
            isPresented: Binding(get: { state.confirming != nil }, set: { if !$0 { state.eventSink(AccountEvent.Dismiss.shared) } }),
            presenting: state.confirming
        ) { ending in
            Button(role: .destructive) {
                state.eventSink(AccountEvent.Confirm(signInAgain: ending.signInAgain))
            } label: {
                Text(AccountTexts.shared.endAction(ending: ending))
            }
            Button(.actionCancel, role: .cancel) {}
        } message: { ending in
            Text(AccountTexts.shared.endMessage(ending: ending))
        }
        .alert(
            Text(.exportDataSharedTitle),
            // Asked only once the share sheet has gone: an alert can't show over it.
            isPresented: Binding(get: { state.askingKeptShared && !share.sharing }, set: { _ in })
        ) {
            Button(.exportDataSharedDelete, role: .destructive) { state.eventSink(AccountEvent.KeptShared(kept: true)) }
            Button(.exportDataSharedKeep, role: .cancel) { state.eventSink(AccountEvent.KeptShared(kept: false)) }
        } message: {
            Text(.exportDataSharedMessage)
        }
    }

    /// The sign-in's status, what an outdated one is missing, and Sign in again where that would help.
    private var grant: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(AccountTexts.shared.signIn(grant: state.grant))
            let missing = AccountTexts.shared.missingFeatures(grant: state.grant)
            if !missing.isEmpty { BulletList(lines: missing) }
            if state.offersSignInAgain {
                AppSecondaryButton(.actionSignInAgain) { state.eventSink(AccountEvent.SignInAgain.shared) }
                    .disabled(state.ending)
            }
        }
        .padding(.vertical, 4)
    }

    /// The data export: what it holds and Export, then its progress, the finished archive to save, or why it stopped; and a failure.
    private var export: some View {
        let export = state.export
        let lines = AccountTexts.shared.exportLines(export: export)
        return VStack(alignment: .leading, spacing: 8) {
            if let running = export as? DataExportStatus.Running {
                HStack(spacing: 12) {
                    ProgressView()
                    Text(AccountTexts.shared.running(export: running))
                }
            } else {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in Text(line) }
            }
            ForEach(Array(AccountTexts.shared.exportControls(export: export).enumerated()), id: \.offset) { _, control in
                ExportControlButton(control: control) {
                    switch control.action {
                    case .Export: state.eventSink(AccountEvent.Export.shared)
                    case .Cancel: state.eventSink(AccountEvent.CancelExport.shared)
                    case .Save: if let ready = export as? DataExportStatus.Ready { save(ready.archive) }
                    }
                }
                // Save waits while the share sheet is up.
                .disabled(control.action == .Save && share.sharing)
            }
            if let error = state.exportError {
                ErrorMessage(error, layout: .row)
            }
        }
        .padding(.vertical, 4)
    }

    /// Writes the archive and offers it in the share sheet; the presenter hears how that ended.
    private func save(_ archive: ExportArchive) {
        let sink = state.eventSink
        Task {
            if let event = await share.share(archive) { sink(event) }
        }
    }
}

#if DEBUG
    #Preview("Account") {
        SamplePages(Samples.account.merging(Samples.accountVariants) { first, _ in first }) { state in
            NavigationStack { AccountView(state: state) }
        }
    }

    #Preview("Account beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!)
                        .environment(\.settingsLayout, .beside(SettingsRows.account))
                },
                page: AccountView(state: Samples.account["exportRunning"]!).environment(\.inPane, true))
        }
    }
#endif

/// One of a data export's buttons, as Kotlin chose it (`ExportControl`): the step's primary one filled, the others tinted. The Account
/// and Leave pages both draw their export with it.
struct ExportControlButton: View {
    let control: ExportControl
    let action: () -> Void

    var body: some View {
        Group {
            if control.primary {
                AppButton(control.label, action: action)
            } else {
                AppSecondaryButton(control.label, action: action)
            }
        }
        .disabled(!control.enabled)
    }
}
