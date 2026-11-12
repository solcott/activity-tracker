import SwiftUI

/// The owner's combination editor (feed/combined-sources), as the shared sheet offers it: the members, each removable after a confirmation,
/// and where each kind of data comes from, each kind with its source (a menu when more than one member recorded it) and its switch. Every
/// change applies at once, so the bar has only Done. Every word and event is Kotlin's (`CombinationTexts`).
struct EditCombinationSheet: View {
    let page: ActivityDetailPage

    @Environment(\.dismiss) private var dismiss
    @State private var removing: MemberView?

    private var texts: CombinationTexts { CombinationTexts.shared }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Array((page.activity.members ?? []).enumerated()), id: \.offset) { _, member in
                        HStack(spacing: 12) {
                            Text(RecordingTexts.shared.memberName(member: member)).frame(maxWidth: .infinity, alignment: .leading)
                            Button(role: .destructive) { removing = member } label: {
                                Image(.close).frame(minWidth: 32, minHeight: 32).contentShape(Rectangle())
                            }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(Text(texts.remove(member: member)))
                        }
                    }
                } header: {
                    Text(texts.members)
                }
                if let sources = page.dataSources {
                    DataSourcesSection(sources: sources, send: send)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(Text(texts.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button { dismiss() } label: { Text(texts.done) }
                }
            }
            .alert(
                Text(removing.map { texts.remove(member: $0) } ?? texts.title),
                isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })
            ) {
                if let member = removing {
                    Button(role: .destructive) { send(texts.removeEvent(member: member)) } label: { Text(texts.remove(member: member)) }
                }
                Button(role: .cancel) {} label: { Text(texts.cancel) }
            } message: {
                Text(texts.removeMessage)
            }
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }
}

/// Each kind of the combined activity's data: its name, then where it comes from (a menu of the members that recorded it, or the one
/// that did, or Left out) and a spinner while it's rebuilt, then its switch, the controls staying on one line at the trailing edge.
private struct DataSourcesSection: View {
    let sources: DataSources
    let send: (any ActivityDetailEvent) -> Void

    private var texts: CombinationTexts { CombinationTexts.shared }

    var body: some View {
        let idle = texts.idle(sources: sources)
        Section {
            if sources.isLoading {
                AppProgress(small: true).frame(maxWidth: .infinity)
            }
            if let error = sources.error {
                ErrorMessage(error, layout: .row)
                Button { send(ActivityDetailEvent.RetrySources.shared) } label: { Text(texts.retry) }
            }
            ForEach(Array(sources.kinds.elements(as: DataSourceRow.self).enumerated()), id: \.offset) { _, row in
                HStack(spacing: 8) {
                    Text(texts.kind(kind: row.kind)).frame(maxWidth: .infinity, alignment: .leading)
                    Group {
                        source(row, enabled: idle)
                        if texts.isSaving(sources: sources, row: row) { AppProgress(small: true) }
                        Toggle(isOn: Binding(get: { row.included }, set: { send(texts.includeEvent(row: row, included: $0)) })) {
                            Text(texts.include(row: row))
                        }
                        .labelsHidden()
                        .disabled(!idle)
                    }
                    .fixedSize()
                }
            }
        } header: {
            Text(texts.dataSources)
        }
    }

    @ViewBuilder private func source(_ row: DataSourceRow, enabled: Bool) -> some View {
        if !row.included {
            Text(texts.leftOut).font(.subheadline).foregroundStyle(.secondary)
        } else if let chosen = row.chosen, texts.offersMenu(row: row) {
            Menu {
                ForEach(Array(row.offered.elements(as: MemberView.self).enumerated()), id: \.offset) { _, member in
                    Button {
                        if let event = texts.chooseEvent(row: row, member: member) { send(event) }
                    } label: {
                        if member.uri.value == chosen.uri.value {
                            Label { Text(texts.sourceName(member: member)) } icon: { Image(systemName: "checkmark") }
                        } else {
                            Text(texts.sourceName(member: member))
                        }
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(texts.sourceName(member: chosen))
                    Image(systemName: "chevron.up.chevron.down").font(.caption2)
                }
                .font(.subheadline)
            }
            .disabled(!enabled)
            .accessibilityLabel(Text(texts.comesFrom(row: row, member: chosen)))
        } else if let chosen = row.chosen {
            Text(texts.from(member: chosen)).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}

#if DEBUG
    #Preview("Edit combination") {
        EditCombinationSheet(page: Samples.combinedPage)
    }
#endif
