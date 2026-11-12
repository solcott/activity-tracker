import IosShell
import SwiftUI
import UniformTypeIdentifiers

/// Importing activities (feed/batch-import, feed/import-duplicates), drawn from the states `ImportActivityPresenter` publishes. Files are
/// picked with the system's document picker, several at once and of any type (iOS has no type for `.fit`; the presenter checks them), and
/// reach Kotlin as sources it reads a range at a time (`IosApp.importSources`, `FilesPicked`). Then one of: the after-import panel; a
/// saved batch's result with its combine suggestions; a batch's summary, filters, rows by month and settings, with an opened row in a
/// sheet (`ImportBatchSections`); or one activity's preview (its page as it would publish, drawn with the activity page's pieces) and
/// form, with Save in the bar. From a regular width of 840 points the preview and the form sit side by side, as the shared screen lays
/// them out from 840dp. Every word and condition is Kotlin's (`ImportActivityTexts`, the state's `canSave` and `saveEvent`), shared with
/// the Compose screen. Opened with `combineInto` ("Add a recording…" on the activity page) it names the activity and shows no
/// visibility choices.
struct ImportActivityView: View {
    let state: ImportActivityState

    @State private var picking = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    private var texts: ImportActivityTexts { ImportActivityTexts.shared }

    var body: some View {
        content
            .fileImporter(isPresented: $picking, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                guard case .success(let urls) = result, !urls.isEmpty else { return }
                let sources = IosApp.shared.importSources(urls: urls.map(\.absoluteString))
                if !sources.isEmpty {
                    state.eventSink(ImportActivityEvent.FilesPicked(sources: sources))
                }
            }
            .pageTitle(Text(texts.title(addsRecording: state.addsRecording))) {
                if showsBarSave {
                    Button { state.eventSink(state.saveEvent(source: CatalogText.shared)) } label: { Text(texts.save(saving: state.saving)) }
                        .fontWeight(.semibold)
                        .disabled(!state.canSave)
                }
            }
    }

    /// One activity's Save, until it's saved and the after-import panel replaces the form.
    private var showsBarSave: Bool {
        state.preview != nil && state.batch == nil && state.afterImport == nil && state.savedSuggestions == nil && state.batchSaved == nil
    }

    @ViewBuilder private var content: some View {
        if let after = state.afterImport {
            AfterImportList(after: after, send: send)
        } else if state.savedSuggestions != nil || state.batchSaved != nil {
            SavedImportList(state: state)
        } else if let batch = state.batch {
            List {
                ImportPickSections(state: state) { picking = true }
                ImportBatchSections(state: state, batch: batch)
            }
            .listStyle(.insetGrouped)
            .sheet(
                isPresented: Binding(get: { state.batch?.preview != nil }, set: { shown in if !shown { send(ImportActivityEvent.ItemClosed.shared) } })
            ) {
                if let item = state.batch?.preview {
                    ImportItemSheet(state: state, item: item).id(item.index)
                }
            }
        } else if let preview = state.preview {
            GeometryReader { geometry in
                if sizeClass == .regular && geometry.size.width >= 840 {
                    HStack(spacing: 0) {
                        List {
                            ImportPickSections(state: state) { picking = true }
                            ImportPreviewSections(state: state, preview: preview, wide: true)
                        }
                        .listStyle(.insetGrouped)
                        .frame(maxWidth: 900)
                        Divider()
                        List { ImportFormSections(state: state) }
                            .listStyle(.insetGrouped)
                            .frame(width: 380)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    List {
                        ImportPickSections(state: state) { picking = true }
                        ImportPreviewSections(state: state, preview: preview, wide: false)
                        ImportFormSections(state: state)
                    }
                    .listStyle(.insetGrouped)
                }
            }
        } else {
            List { ImportPickSections(state: state) { picking = true } }
                .listStyle(.insetGrouped)
        }
    }

    private func send(_ event: any ImportActivityEvent) {
        state.eventSink(event)
    }
}

/// Picking files, and how reading them went: the hint, Choose files, reading's progress with Cancel, a failure before anything was read,
/// a batch save under way that this screen didn't start, and each stopped import with Dismiss.
struct ImportPickSections: View {
    let state: ImportActivityState
    let pick: () -> Void

    var body: some View {
        let texts = ImportActivityTexts.shared
        let picked = state.picked
        Section {
            if !picked {
                Text(texts.hint).font(.subheadline)
            }
            Button(action: pick) {
                Label { Text(texts.pick(picked: picked)) } icon: { Image(systemName: "doc.badge.plus") }
            }
            .disabled(state.reading || state.saving)
            if state.reading {
                HStack(spacing: 12) {
                    AppProgress(small: true)
                    Text(texts.reading(progress: state.readProgress)).font(.subheadline).foregroundStyle(.secondary)
                }
                Button { state.eventSink(ImportActivityEvent.CancelReading.shared) } label: { Text(texts.cancel) }
            }
            // Once files are read, errors are saving's, shown with the form.
            if !picked, let error = state.error {
                ErrorMessage(error, layout: .row)
            }
            if picked && state.originalTooLarge {
                Text(texts.originalTooLarge).font(.footnote).foregroundStyle(.secondary)
            }
        }
        if !picked {
            if let saving = state.savingImport {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(texts.savingTitle).font(.headline)
                        Text(texts.savingProgress(saving: saving)).font(.subheadline)
                    }
                    Button { state.eventSink(ImportActivityEvent.Saved.ViewUploads.shared) } label: { Text(texts.viewUploads) }
                }
            }
            ForEach(state.stoppedImports.elements(as: StoppedImport.self), id: \.batchId) { stopped in
                Section {
                    Text(texts.stopped(stopped: stopped)).font(.subheadline)
                    Button { state.eventSink(ImportActivityEvent.StoppedImportDismissed(batchId: stopped.batchId)) } label: {
                        Text(texts.stoppedDismiss)
                    }
                }
            }
        }
    }
}

/// One activity read from a file: its name and time zone, the duplicate search and what to do with a duplicate, the activity a recording
/// is added to, then what its page will show.
struct ImportPreviewSections: View {
    let state: ImportActivityState
    let preview: ImportPreview
    let wide: Bool

    var body: some View {
        let texts = ImportActivityTexts.shared
        let enabled = !state.saving
        Section {
            Text(verbatim: preview.fileName).font(.headline)
            if let clock = preview.clock {
                TimeZoneRows(notice: texts.zoneNotice(clock: clock, fileName: nil), clock: clock, enabled: enabled) { id in
                    state.eventSink(ImportActivityEvent.TimeZoneChanged(id: id))
                }
            }
        }
        DuplicateBannerSection(banner: texts.duplicateBanner(state: state), check: state.duplicateCheck) {
            state.eventSink(ImportActivityEvent.RetryDuplicateCheck.shared)
        }
        if let duplicate = preview.duplicate {
            Section {
                DuplicateWarningView(info: duplicate, inFileNumber: nil, enabled: enabled) { action in
                    state.eventSink(ImportActivityEvent.DuplicateActionChanged(index: 0, action: action))
                }
            }
        }
        if let target = state.addingTo {
            Section {
                Text(texts.addingTo(target: target)).font(.subheadline)
                if state.tooFarFromTarget {
                    Text(texts.tooFar).font(.footnote).foregroundStyle(.red)
                }
            }
        }
        ImportPageSections(preview: preview, page: state.page, wide: wide)
    }
}

/// What the activity's page will show once saved, read-only, with the activity page's own pieces: its start, hero, map, avg/max table
/// (beside the form only, as the shared screen shows it from 600dp), training load, cards and stats; a spinner until it's built.
struct ImportPageSections: View {
    let preview: ImportPreview
    let page: ActivityPageContent?
    let wide: Bool

    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats

    var body: some View {
        let texts = ImportActivityTexts.shared
        if let page {
            if let start = page.start {
                Section {
                    HStack(spacing: 8) {
                        Text(verbatim: FigureValueText.string(start.value, units: stats.units, clock: clock)).font(.footnote).foregroundStyle(.secondary)
                        MarkBadge(mark: start.mark)
                    }
                }
            }
            let hero = page.hero.elements(as: Figure.self)
            if !hero.isEmpty {
                Section { HeroFigures(figures: hero).padding(.vertical, 4) }
            }
            if let map = texts.routeMap(preview: preview) {
                Section {
                    RouteMapView(map: map).listRowInsets(EdgeInsets())
                    if let lock = texts.mapLock(page: page) {
                        HStack(spacing: 4) {
                            Image(.lock)
                            Text(lock)
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(texts.mapLockDescription(page: page).map { Text($0) } ?? Text(lock))
                    }
                }
            }
            if wide {
                AvgMaxSection(content: page)
            }
            TrainingLoadSection(figures: page.trainingLoad.elements(as: Figure.self))
            if let charts = page.charts {
                ForEach(Array(page.cards.elements(as: MetricCard.self).enumerated()), id: \.offset) { _, card in
                    MetricCardSection(card: card, charts: charts, missingZones: false, caption: nil, setThresholds: {})
                }
            }
            StatsSections(groups: page.stats.elements(as: StatsGroup.self)) { _ in }
        } else {
            Section { AppProgress().frame(maxWidth: .infinity) }
        }
    }
}

/// The choices saving keeps for one activity: a failed save, the type, the title and details, threshold changes, gear and its
/// conflicts, the new devices, and who may see it (not for a recording added to an activity, which takes that activity's).
struct ImportFormSections: View {
    let state: ImportActivityState

    var body: some View {
        let texts = ImportActivityTexts.shared
        let enabled = !state.saving
        if let error = state.error {
            Section { ErrorMessage(error, layout: .row) }
        }
        Section { ActivityTypeRow(field: state.type, enabled: enabled) }
        let hint = state.defaultTitle.map { texts.defaultTitle(title: $0) }
        if let details = state.details {
            ActivityDetailsSections(title: state.title, titleHint: hint, fields: details, enabled: enabled)
        } else {
            ImportTitleSection(title: state.title, hint: hint, enabled: enabled)
        }
        let changes = state.thresholdChanges.elements(as: ThresholdChange.self)
        if !changes.isEmpty {
            Section {
                ForEach(Array(changes.enumerated()), id: \.offset) { _, change in
                    Text(texts.thresholdChange(change: change)).font(.subheadline)
                }
            } header: {
                Text(texts.thresholdsHeading)
            }
        }
        if let gear = state.gear {
            GearSection(gear: gear, enabled: enabled)
        }
        NewDevicesSections(fields: state.newDevices, enabled: enabled)
        if !state.addsRecording {
            ImportVisibilitySections(fields: state.visibility, heading: Text(.activityVisibilityHeading), enabled: enabled)
        }
        if state.saving {
            Section { AppProgress().frame(maxWidth: .infinity) }
        }
    }
}

/// The title alone, for an activity with no details form.
struct ImportTitleSection: View {
    @State private var model: TextFieldModel
    private let title: KotlinTextFieldState
    private let hint: (any AppText)?
    private let enabled: Bool

    init(title: KotlinTextFieldState, hint: (any AppText)?, enabled: Bool) {
        self.title = title
        self.hint = hint
        self.enabled = enabled
        _model = State(initialValue: TextFieldModel(title))
    }

    var body: some View {
        Section {
            AppTextField(label: .fieldTitle, model: model, state: title, layout: .row, hint: hint.map { Text($0) })
                .disabled(!enabled)
        }
    }
}

/// The gear (for a batch, every activity's), with any sensors that matched more than one item of a type.
struct GearSection: View {
    let gear: GearField
    let enabled: Bool
    var footer: (any AppText)?

    var body: some View {
        let texts = ImportActivityTexts.shared
        Section {
            GearFieldRow(field: gear, enabled: enabled)
            ForEach(Array(gear.conflicts.elements(as: GearConflict.self).enumerated()), id: \.offset) { _, conflict in
                Text(texts.gearConflict(conflict: conflict)).font(.footnote).foregroundStyle(.red)
            }
        } footer: {
            if let footer { Text(footer) }
        }
    }
}

/// Who may see the activity, or the batch's: the visibility sections every activity form places.
struct ImportVisibilitySections: View {
    let fields: VisibilityFields
    let heading: Text
    let enabled: Bool

    var body: some View {
        VisibilityFormSections(
            fields: fields, heading: heading, activityEnabled: enabled && fields.canChoose, categoriesEnabled: enabled
        ) {
            if fields.canChoose {
                Text(.activityVisibilityHelp)
            } else {
                Text(VisibilityTexts.shared.lockedNote(fields: fields))
            }
        } detailsNote: {
            EmptyView()
        }
    }
}

/// The duplicate search's progress, its failure with Retry, or (for a batch) how many duplicates it found; nothing when there's nothing
/// to say.
struct DuplicateBannerSection: View {
    let banner: (any AppText)?
    let check: DuplicateCheck
    let retry: () -> Void

    var body: some View {
        if let banner {
            Section {
                Text(banner).font(.subheadline)
                if ImportActivityTexts.shared.offersRetry(check: check) {
                    Button(action: retry) { Text(ImportActivityTexts.shared.retry) }
                }
            }
        }
    }
}

/// Why an activity looks like a duplicate, what to do with it as segments (Combine left out while the logged activity uploads), and
/// what the chosen action does.
struct DuplicateWarningView: View {
    let info: DuplicateInfo
    let inFileNumber: Int32?
    let enabled: Bool
    let onAction: (DuplicateAction) -> Void

    var body: some View {
        let texts = ImportActivityTexts.shared
        let choices = texts.duplicateChoices(info: info)
        VStack(alignment: .leading, spacing: 8) {
            Text(texts.duplicateWarning(info: info, inFileNumber: inFileNumber ?? info.inBatchIndex.map { $0 + 1 }))
                .font(.footnote)
                .foregroundStyle(.red)
                .fixedSize(horizontal: false, vertical: true)
            Picker(selection: Binding(get: { info.action }, set: onAction)) {
                ForEach(choices, id: \.action) { choice in
                    Text(texts.action(action: choice.action)).tag(choice.action)
                }
            } label: {
                Text(texts.action(action: info.action))
            }
            .pickerStyle(.segmented)
            .disabled(!enabled)
            if let explained = texts.duplicateExplained(info: info) {
                Text(explained).font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 4)
    }
}

/// A zone-less file's notice, and its time zone as a row that opens a searchable list of zones.
struct TimeZoneRows: View {
    let notice: any AppText
    let clock: ImportClock
    let enabled: Bool
    let onPick: (String) -> Void

    @State private var choosing = false

    var body: some View {
        let texts = ImportActivityTexts.shared
        Text(notice).font(.footnote).foregroundStyle(.secondary)
        AppListRow(Text(texts.timeZone), summary: Text(texts.zone(zone: clock.zone)), opens: { choosing = true })
            .disabled(!enabled)
            .sheet(isPresented: $choosing) {
                TimeZoneSheet(clock: clock, onPick: onPick)
            }
    }
}

/// Every zone to pick one from, found by part of its city or its offset (`ImportActivityTexts.zones`, the shared rule), the chosen one
/// checked. Picking closes the sheet.
struct TimeZoneSheet: View {
    let clock: ImportClock
    let onPick: (String) -> Void

    @State private var query: String
    @Environment(\.dismiss) private var dismiss

    init(clock: ImportClock, query: String = "", onPick: @escaping (String) -> Void) {
        self.clock = clock
        self.onPick = onPick
        _query = State(initialValue: query)
    }

    var body: some View {
        let texts = ImportActivityTexts.shared
        NavigationStack {
            List(texts.zones(clock: clock, query: query), id: \.id) { zone in
                let chosen = zone.id == clock.zone.id
                Button {
                    onPick(zone.id)
                    dismiss()
                } label: {
                    HStack {
                        Text(texts.zone(zone: zone)).foregroundStyle(.primary)
                        Spacer()
                        if chosen {
                            Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(chosen ? .isSelected : [])
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always))
            .navigationTitle(Text(texts.timeZone))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(.actionCancel) { dismiss() } }
            }
        }
    }
}

/// The import is saved: that its links weren't, and the offer to add the new gear to earlier imports with its progress, before leaving.
struct AfterImportList: View {
    let after: AfterImport
    let send: (any ImportActivityEvent) -> Void

    var body: some View {
        let texts = ImportActivityTexts.shared
        List {
            Section {
                Text(texts.afterTitle).font(.headline)
                if after.linksNotSaved {
                    Text(texts.afterLinksNotSaved).font(.subheadline).foregroundStyle(.red)
                }
            }
            if let past = after.pastImports {
                Section {
                    Text(texts.pastOffer(past: past)).font(.subheadline)
                    if past.applying {
                        HStack(spacing: 12) {
                            AppProgress(small: true)
                            Text(texts.pastProgress(past: past)).font(.footnote).foregroundStyle(.secondary)
                        }
                    } else {
                        Button { send(ImportActivityEvent.After.ApplyToPast.shared) } label: { Text(texts.pastApply) }
                        Button { send(ImportActivityEvent.After.Done.shared) } label: { Text(texts.pastSkip) }
                    }
                }
            } else {
                Section {
                    Button { send(ImportActivityEvent.After.Done.shared) } label: { Text(texts.afterDone) }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

/// A batch just saved: how many were saved and skipped and how they upload, then the activities that likely belong with ones already
/// logged, each opening its page to combine it, View uploads and Done.
struct SavedImportList: View {
    let state: ImportActivityState

    @Environment(\.clock) private var clock

    var body: some View {
        let texts = ImportActivityTexts.shared
        let suggestions = state.savedSuggestions?.elements(as: SavedSuggestion.self) ?? []
        List {
            if let result = state.batchSaved {
                Section {
                    Text(texts.batchSavedTitle).font(.headline)
                    Text(texts.batchSaved(result: result, mobile: true)).font(.subheadline)
                }
            }
            if !suggestions.isEmpty {
                Section {
                    ForEach(suggestions, id: \.uri.value) { suggestion in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(texts.suggestionCue(suggestion: suggestion)).font(.subheadline.weight(.semibold))
                            clock.text(texts.suggestionRow(suggestion: suggestion, now: clock.now, zone: clock.kotlinZone)).font(.subheadline)
                            AppSecondaryButton(texts.suggestionCombine, small: true) {
                                send(ImportActivityEvent.Saved.Open(uri: suggestion.uri))
                            }
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text(texts.suggestionsTitle)
                } footer: {
                    Text(texts.suggestionsExplained)
                }
            }
            Section {
                if state.batchSaved != nil {
                    Button { send(ImportActivityEvent.Saved.ViewUploads.shared) } label: { Text(texts.viewUploads) }
                }
                Button { send(ImportActivityEvent.Saved.Done.shared) } label: { Text(texts.savedDone).fontWeight(.semibold) }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func send(_ event: any ImportActivityEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Import") {
        SamplePages(Samples.importActivity) { state in NavigationStack { ImportActivityView(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Import, beyond these") {
        SamplePages(Samples.importActivityVariants) { state in NavigationStack { ImportActivityView(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
