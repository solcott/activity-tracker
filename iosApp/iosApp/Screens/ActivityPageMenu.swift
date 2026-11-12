import IosShell
import SwiftUI
import UIKit

/// The activity page's bar menus (feed/activity-page-layout): the owner's "…" menu as Kotlin lists it (`ActivityPageTexts.ownerMenu`),
/// each item sending its event, Split and Delete asking first in the shared words and Export opening its sheet; and Report on someone
/// else's activity, which opens the native report sheet. The built export file goes to the system's share sheet.
struct ActivityPageMenu: View {
    let page: ActivityDetailPage
    @Binding var exporting: Bool

    @State private var splitting = false
    @State private var deleting = false

    var body: some View {
        let texts = ActivityPageTexts.shared
        if let manage = page.manage {
            let items = texts.ownerMenu(page: page)
            Menu {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button(role: item.destructive ? .destructive : nil) { choose(item) } label: {
                        Label { Text(item.label) } icon: { Image(Self.icon(item.action)) }
                    }
                }
            } label: {
                Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .disabled(!texts.menuEnabled(manage: manage))
            .accessibilityLabel(Text(texts.more))
            .alert(Text(texts.splitTitle), isPresented: $splitting) {
                Button(role: .destructive) { send(ActivityDetailEvent.Split.shared) } label: { Text(texts.split) }
                Button(role: .cancel) {} label: { Text(texts.cancel) }
            } message: {
                Text(texts.splitMessage)
            }
            .alert(Text(texts.deleteTitle), isPresented: $deleting) {
                ForEach(Array(texts.deleteChoices(manage: manage).enumerated()), id: \.offset) { _, choice in
                    Button(role: .destructive) { send(choice.event()) } label: { Text(choice.label) }
                }
                Button(role: .cancel) {} label: { Text(texts.cancel) }
            } message: {
                Text(texts.deleteMessage(manage: manage))
            }
        } else if texts.offersReport(page: page) {
            Menu {
                Button {
                    if let report = texts.reportEvent(page: page, authorName: AppStrings.string(texts.reportName(activity: page.activity))) {
                        page.reporting.eventSink(report)
                    }
                } label: {
                    Text(texts.report)
                }
            } label: {
                Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(Text(texts.more))
        }
    }

    private func choose(_ item: ActivityMenuItem) {
        if let event = item.event() {
            send(event)
            return
        }
        switch item.action {
        case .Split: splitting = true
        case .Export: exporting = true
        default: deleting = true
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }

    /// The icon beside each item, as the shared menu's.
    static func icon(_ action: ActivityMenuAction) -> AppIcon {
        switch action {
        case .Edit: .manualEntry
        case .Combine, .AddRecording, .CreateSegment: .add
        case .Split, .Ungroup: .ungroup
        case .HideStart, .HideEnd: .hide
        case .CreateDiveSite: .location
        case .Export: .export
        default: .delete
        }
    }
}

/// The owner's export (feed/activity-export): the original file the activity was imported from, if any, apart and first; the rebuilt
/// formats the activity's data allows, with the note that such a file is rebuilt and holds the untrimmed route; and the build's progress
/// or error. In the bar, Cancel and the action Kotlin names (Export, or Download when there's only the original).
struct ActivityExportSheet: View {
    let export: ActivityExportState
    let send: (any ActivityDetailEvent) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selected = 0

    var body: some View {
        let texts = ActivityPageTexts.shared
        let count = Int(texts.formatCount(export: export))
        NavigationStack {
            Form {
                if let original = export.original {
                    Section {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(verbatim: original.name).font(.body)
                            Text(texts.exportOriginalExplained).font(.footnote).foregroundStyle(.secondary)
                        }
                        if count > 0 {
                            Button { send(ActivityDetailEvent.DownloadOriginal.shared) } label: { Text(texts.exportDownload) }
                                .disabled(export.busy)
                        }
                    } header: {
                        Text(texts.exportOriginal)
                    }
                }
                if count > 0 {
                    Section {
                        if count > 1 {
                            Picker(selection: $selected) {
                                ForEach(0..<count, id: \.self) { index in
                                    Text(texts.formatName(export: export, index: Int32(index))).tag(index)
                                }
                            } label: {
                                Text(texts.exportRebuilt)
                            }
                            .pickerStyle(.segmented)
                            .disabled(export.busy)
                        } else {
                            Text(texts.formatName(export: export, index: 0)).font(.headline)
                        }
                        Text(texts.exportNote(export: export)).font(.footnote).foregroundStyle(.secondary)
                    } header: {
                        if export.original != nil { Text(texts.exportRebuilt) }
                    }
                }
                if export.busy {
                    HStack(spacing: 8) {
                        AppProgress(small: true)
                        Text(texts.exportPreparing).font(.footnote)
                    }
                }
                if let error = export.error {
                    ErrorMessage(error, layout: .row)
                }
            }
            .navigationTitle(Text(texts.exportTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Text(texts.cancel) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        if let event = texts.exportEvent(export: export, selected: Int32(selected)) { send(event) }
                    } label: {
                        Text(texts.exportAction(export: export))
                    }
                    .disabled(export.busy)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Handing a built export file to the share sheet: Kotlin writes it into the caches folder, the system's sheet offers it, and the copy
/// goes once the sheet has closed, whether it was shared, saved or cancelled.
enum ActivityExportHandOff {
    @MainActor
    static func share(_ file: ExportedFile) async {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appending(path: file.name)
        // Kotlin's classes aren't Sendable to Swift; the file is immutable on Kotlin's side.
        nonisolated(unsafe) let file = file
        let written = (try? await IosApp.shared.writeExport(file: file, path: url.path(percentEncoded: false))) ?? false
        if written {
            _ = await ArchiveShare.presentShareSheet(url)
            try? FileManager.default.removeItem(at: url)
        }
    }
}
