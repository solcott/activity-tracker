import SwiftUI

/// The upload queue (data/upload-queue), drawn from the states `UploadsPresenter` publishes: Clear in the bar; the last action's failure;
/// the athlete's and the server's pauses and the time left; the item uploading now, the athlete's own items, each import's group, the
/// finished items and the other refused changes, each item with Send again and Cancel where it has them; and the system's alert before
/// cancelling. What it says and when each control shows or can be used are Kotlin's (`UploadsTexts`, `UploadImportTexts`, `UploadsState`),
/// shared with the Compose page; the day words are read on the environment's `clock`.
struct UploadsView: View {
    let state: UploadsState
    @Environment(\.clock) private var clock

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadUploads, retry: { send(UploadsEvent.Retry.shared) }) {
            if let queue = state.queue { list(queue) }
        }
        .pageTitle(Text(.uploadsTitle)) { clear }
        .alert(
            Text(.uploadsCancelTitle),
            isPresented: Binding(get: { state.confirmingCancel != nil }, set: { if !$0 { send(UploadsEvent.Dismiss.shared) } }),
            presenting: state.confirmingCancel
        ) { rkey in
            Button(.uploadsCancelConfirm, role: .destructive) { send(UploadsEvent.ConfirmCancel(rkey: rkey)) }
            Button(.uploadsKeep, role: .cancel) {}
        } message: { _ in
            Text(.uploadsCancelMessage)
        }
        .alert(
            Text(.uploadsCancelRestTitle),
            isPresented: Binding(get: { state.confirmingCancelRest != nil }, set: { if !$0 { send(UploadsEvent.Dismiss.shared) } }),
            presenting: state.confirmingCancelRest
        ) { batchId in
            Button(.uploadsCancelRest, role: .destructive) { send(UploadsEvent.ConfirmCancelRest(batchId: batchId)) }
            Button(.uploadsKeep, role: .cancel) {}
        } message: { _ in
            Text(UploadsTexts.shared.cancelRestMessage(state: state))
        }
    }

    private func list(_ queue: UploadQueueView) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadUploads) { send(UploadsEvent.Retry.shared) }
                .listStatusRow()
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            banners(queue)
            if let current = queue.current {
                Section { itemRow(current) } header: { Text(.uploadsUploadingNow) }
            }
            if !queue.own.isEmpty {
                Section { ForEach(queue.own, id: \.id) { itemRow($0) } } header: { Text(.uploadsYours) }
            }
            ForEach(queue.imports, id: \.id) { group in importSection(group) }
            if !queue.finished.isEmpty {
                Section { ForEach(queue.finished, id: \.id) { itemRow($0) } } header: { Text(.uploadsFinished) }
            }
            if !queue.refusedOther.isEmpty {
                Section { ForEach(queue.refusedOther, id: \.rkey) { changeRow($0) } } header: { Text(.uploadsRefusedOther) }
            }
            if queue.isEmpty {
                Text(.uploadsEmpty).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .listStatusRow()
            }
        }
        .listStyle(.insetGrouped)
    }

    /// The pauses, the time left and where uploads go on, as one section when any shows.
    @ViewBuilder private func banners(_ queue: UploadQueueView) -> some View {
        let serverPaused = UploadsTexts.shared.serverPaused(state: state, now: clock.now, zone: clock.kotlinZone)
        let timeLeft = UploadsTexts.shared.timeLeft(state: state)
        if state.importsPaused || state.showsPauseImports || serverPaused != nil || timeLeft != nil || state.isOpenOnly {
            Section {
                if state.importsPaused {
                    HStack {
                        Text(.uploadsImportsPaused).frame(maxWidth: .infinity, alignment: .leading)
                        AppSecondaryButton(.uploadsResumeImports, small: true) { send(UploadsEvent.ResumeImports.shared) }
                            .disabled(state.changingPause)
                    }
                } else if state.showsPauseImports {
                    AppSecondaryButton(.uploadsPauseImports, small: true) { send(UploadsEvent.PauseImports.shared) }
                        .disabled(state.changingPause)
                }
                if let serverPaused { note(clock.text(serverPaused)) }
                if let timeLeft { note(Text(timeLeft)) }
                if state.isOpenOnly { note(Text(.uploadsOpenOnly)) }
            }
        }
    }

    /// An import's header, its live items (the first 20 until Show all), then its finished ones.
    private func importSection(_ group: UploadImport) -> some View {
        Section {
            importHeader(group)
            ForEach(state.shownLive(group: group), id: \.id) { itemRow($0) }
            if state.showsShowAll(group: group) {
                Button { send(UploadsEvent.ShowAll(batchId: group.id)) } label: { Text(UploadImportTexts.shared.showAll(group: group)) }
            }
            ForEach(group.finished, id: \.id) { itemRow($0) }
        }
    }

    private func importHeader(_ group: UploadImport) -> some View {
        let texts = UploadImportTexts.shared
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(texts.source(source: group.source)).font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                if state.offersCancelRest(group: group) {
                    AppTextButton(.uploadsCancelRest) { send(UploadsEvent.CancelRest(batchId: group.id)) }
                        .disabled(state.isBusy(key: group.id))
                        .accessibilityLabel(Text(texts.cancelRest(group: group)))
                }
            }
            clock.text(texts.saved(group: group, now: clock.now, zone: clock.kotlinZone)).font(.subheadline).foregroundStyle(.secondary)
            if let save = texts.saveStatus(group: group) {
                Text(save).font(.subheadline).foregroundStyle(group.save is ImportSaveStopped ? AnyShapeStyle(.red) : AnyShapeStyle(.primary))
            }
            Text(texts.progress(group: group)).font(.subheadline)
            if let counts = texts.counts(group: group) {
                Text(counts).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    private func itemRow(_ item: UploadItem) -> some View {
        let name = UploadsTexts.shared.itemName(item: item, zone: clock.kotlinZone)
        let busy = item.uri?.rkey.map { state.isBusy(key: $0) } ?? false
        return UploadRow(
            name: clock.string(name), detail: Text(UploadsTexts.shared.state(item: item)), refused: item.state == .Refused,
            opens: state.opens(item: item) ? item.uri.map { uri in { send(UploadsEvent.OpenActivity(uri: uri.value)) } } : nil
        ) {
            if let rkey = item.uri?.rkey {
                if state.offersSendAgain(item: item) { sendAgain(name, rkey: rkey, busy: busy) }
                if state.offersCancel(item: item) { cancel(name, rkey: rkey, busy: busy) }
            }
        }
    }

    private func changeRow(_ change: RejectedChange) -> some View {
        let name = UploadsTexts.shared.changeName(change: change, zone: clock.kotlinZone)
        let busy = state.isBusy(key: change.rkey)
        return UploadRow(name: clock.string(name), detail: Text(.uploadsStateRefused), refused: true, opens: nil) {
            sendAgain(name, rkey: change.rkey, busy: busy)
            cancel(name, rkey: change.rkey, busy: busy)
        }
    }

    private func sendAgain(_ name: any AppText, rkey: String, busy: Bool) -> some View {
        AppTextButton(.uploadsSendAgain) { send(UploadsEvent.SendAgain(rkey: rkey)) }
            .disabled(busy)
            .accessibilityLabel(clock.text(UploadsTexts.shared.sendAgain(name: name)))
    }

    private func cancel(_ name: any AppText, rkey: String, busy: Bool) -> some View {
        AppTextButton(.uploadsCancel) { send(UploadsEvent.Cancel(rkey: rkey)) }
            .disabled(busy)
            .accessibilityLabel(clock.text(UploadsTexts.shared.cancel(name: name)))
    }

    private func note(_ text: Text) -> some View {
        text.font(.subheadline).foregroundStyle(.secondary)
    }

    /// Clear, once the queue has loaded: unavailable with nothing finished and while clearing.
    @ViewBuilder private var clear: some View {
        if state.showsClear {
            AppTextButton(.uploadsClear) { send(UploadsEvent.ClearFinished.shared) }
                .disabled(!state.canClear)
                .accessibilityLabel(Text(.uploadsClearDescription))
        }
    }

    private func send(_ event: any UploadsEvent) {
        state.eventSink(event)
    }
}

/// One item of the queue: its name, and its state under it (red when refused), opening its activity when it can, with its actions at the
/// trailing end outside the row's own button, as `AppItemRow` keeps them.
private struct UploadRow<Actions: View>: View {
    let name: String
    let detail: Text
    let refused: Bool
    let opens: (() -> Void)?
    @ViewBuilder let actions: Actions

    var body: some View {
        HStack(spacing: 12) {
            if let opens {
                Button(action: opens) { labels.contentShape(Rectangle()) }.buttonStyle(.plain)
            } else {
                labels
            }
            actions
        }
    }

    private var labels: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: name).foregroundStyle(.primary)
            detail.font(.subheadline).foregroundStyle(refused ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
    #Preview("Uploads") {
        SamplePages(Samples.uploads) { state in NavigationStack { UploadsView(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
