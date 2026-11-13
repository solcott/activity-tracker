import SwiftUI

/// Several activities to import (feed/batch-import): what was read (counts, each export's note, a zone per zone-less file, the duplicate
/// banner, what was skipped, left out or too large), All / New / Duplicates with the selected count, Select all and an action for every
/// shown duplicate, then the rows shown by month (a tick selects one, a tap opens it), the batch's gear, devices and visibility, and Save
/// with its progress. What each says is Kotlin's (`ImportActivityTexts`).
struct ImportBatchSections: View {
    let state: ImportActivityState
    let batch: ImportBatch

    @Environment(\.stats) private var stats

    private var texts: ImportActivityTexts { ImportActivityTexts.shared }

    var body: some View {
        let enabled = !state.saving
        summary
        ForEach(batch.clocks.elements(as: FileClock.self), id: \.key) { file in
            Section {
                TimeZoneRows(notice: texts.zoneNotice(clock: file.clock, fileName: file.fileName), clock: file.clock, enabled: enabled) { id in
                    send(ImportActivityEvent.FileZoneChanged(key: file.key, id: id))
                }
            }
        }
        DuplicateBannerSection(banner: texts.batchDuplicateBanner(state: state, batch: batch), check: state.duplicateCheck) {
            send(ImportActivityEvent.RetryDuplicateCheck.shared)
        }
        filters.disabled(!enabled)
        ForEach(Array(texts.months(batch: batch).enumerated()), id: \.offset) { _, month in
            Section {
                ForEach(month.rows.elements(as: ImportRow.self), id: \.index) { row in
                    ImportBatchRowView(row: row, batch: batch, enabled: enabled, send: send)
                }
            } header: {
                Text(month.heading)
            }
        }
        if let gear = state.gear {
            GearSection(gear: gear, enabled: enabled, footer: texts.detailsHint)
        } else {
            Section {} footer: { Text(texts.detailsHint) }
        }
        NewDevicesSections(fields: state.newDevices, enabled: enabled)
        ImportVisibilitySections(fields: state.visibility, heading: Text(texts.batchVisibilityHeading), enabled: enabled)
        Section {
            if let failure = texts.saveFailure(state: state, batch: batch) {
                Text(failure).font(.subheadline).foregroundStyle(.red)
            }
            Button { send(state.saveBatchEvent(source: CatalogText.shared)) } label: {
                HStack(spacing: 8) {
                    if state.saving { AppProgress(small: true) }
                    Text(texts.saveBatch(state: state, batch: batch)).fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
            }
            .disabled(!state.canSaveBatch)
        } footer: {
            if let size = texts.originalsSize(batch: batch, mobile: true) {
                Text(size)
            }
        }
    }

    @ViewBuilder private var summary: some View {
        Section {
            Text(texts.found(batch: batch)).font(.headline)
            ForEach(Array(batch.exports.elements(as: BatchExport.self).enumerated()), id: \.offset) { _, export in
                Text(texts.exportNote(export: export)).font(.subheadline)
            }
            if let skipped = texts.skipped(batch: batch) {
                Text(skipped).font(.footnote).foregroundStyle(.red)
            }
            if let ignored = texts.ignored(batch: batch) {
                Text(ignored).font(.footnote).foregroundStyle(.secondary)
            }
            if let tooLarge = texts.originalsTooLarge(batch: batch) {
                Text(tooLarge).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder private var filters: some View {
        Section {
            Picker(selection: Binding(get: { batch.filter }, set: { send(ImportActivityEvent.FilterChanged(filter: $0)) })) {
                ForEach(texts.filters, id: \.filter) { choice in
                    Text(texts.filter(filter: choice.filter)).tag(choice.filter)
                }
            } label: {
                Text(texts.filter(filter: batch.filter))
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
        Section {
            HStack(spacing: 12) {
                Text(texts.selected(batch: batch)).font(.subheadline.weight(.medium)).frame(maxWidth: .infinity, alignment: .leading)
                if let selectAll = texts.selectAll(batch: batch) {
                    Button { send(ImportActivityEvent.AllShownSelected(selected: !batch.allShownSelected)) } label: { Text(selectAll) }
                        .buttonStyle(.borderless)
                        .fixedSize()
                }
            }
            if texts.offersBulkActions(batch: batch) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(texts.bulkHeading).font(.footnote).foregroundStyle(.secondary)
                    // Buttons, not segments: the shown duplicates can each be set differently, so none is chosen.
                    FlowRow {
                        ForEach(texts.bulkChoices, id: \.action) { choice in
                            Button { send(ImportActivityEvent.ShownDuplicatesAction(action: choice.action)) } label: {
                                Text(texts.action(action: choice.action)).font(.subheadline)
                            }
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                            .controlSize(.small)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private func send(_ event: any ImportActivityEvent) {
        state.eventSink(event)
    }
}

/// One of a batch's activities: a tick that selects it, then its title, what it is, its file and any narrowing by its export, which
/// opens it; a duplicate says what it repeats and offers what to do with it.
struct ImportBatchRowView: View {
    let row: ImportRow
    let batch: ImportBatch
    let enabled: Bool
    let send: (any ImportActivityEvent) -> Void

    @Environment(\.stats) private var stats

    var body: some View {
        let texts = ImportActivityTexts.shared
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                Button { send(ImportActivityEvent.ItemToggled(index: row.index)) } label: {
                    Image(systemName: row.selected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(row.selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(texts.rowTitle(row: row)))
                .accessibilityAddTraits(row.selected ? .isSelected : [])
                Button { send(ImportActivityEvent.ItemOpened(index: row.index)) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(texts.rowTitle(row: row)).foregroundStyle(.primary)
                        Text(texts.rowDetails(row: row, units: stats.units)).font(.footnote).foregroundStyle(.secondary)
                        Text(verbatim: row.fileName).font(.footnote).foregroundStyle(.secondary)
                        if let narrowed = texts.narrowed(row: row) {
                            Text(narrowed).font(.footnote)
                        }
                    }
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            if let duplicate = row.duplicate {
                DuplicateWarningView(info: duplicate, inFileNumber: texts.inFileNumber(batch: batch, row: row), enabled: enabled) { action in
                    send(ImportActivityEvent.DuplicateActionChanged(index: row.index, action: action))
                }
                .padding(.leading, 36)
            }
        }
        .disabled(!enabled)
    }
}

/// One of a batch's activities opened: its type and title, the only things to change here, a duplicate beside what it repeats, then its
/// page as it would publish. Done or a swipe down closes it.
struct ImportItemSheet: View {
    let state: ImportActivityState
    let item: ImportItemPreview

    @State private var title: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: ImportActivityState, item: ImportItemPreview) {
        self.state = state
        self.item = item
        _title = State(initialValue: TextFieldModel(item.title))
    }

    var body: some View {
        let texts = ImportActivityTexts.shared
        let enabled = !state.saving
        let row = state.batch?.rows.elements(as: ImportRow.self).first { $0.index == item.index }
        NavigationStack {
            List {
                Section {
                    ActivityTypeRow(field: item.type, enabled: enabled)
                    AppTextField(
                        label: texts.itemTitleLabel, model: title, state: item.title, layout: .row,
                        hint: Text(texts.defaultTitle(title: item.defaultTitle))
                    )
                    .disabled(!enabled)
                }
                if let error = item.error {
                    Section { ErrorMessage(error, layout: .row) }
                }
                if let row, let matched = item.matched, let duplicate = row.duplicate {
                    Section {
                        ImportComparison(row: row, matched: matched, duplicate: duplicate)
                    } header: {
                        if duplicate.exactCopy { Text(texts.compareExact) }
                    }
                }
                if let preview = item.preview {
                    Section { Text(verbatim: preview.fileName).font(.headline) }
                    ImportPageSections(preview: preview, page: item.page, wide: false)
                } else if item.error == nil {
                    Section { AppProgress().frame(maxWidth: .infinity) }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(Text(texts.itemTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button { state.eventSink(ImportActivityEvent.ItemClosed.shared) } label: { Text(texts.itemDone) }
                }
            }
        }
    }
}

/// A duplicate beside what it repeats: sport, start, length, distance and device, a column each.
struct ImportComparison: View {
    let row: ImportRow
    let matched: ExistingActivityPreview
    let duplicate: DuplicateInfo

    @Environment(\.stats) private var stats

    var body: some View {
        let texts = ImportActivityTexts.shared
        HStack(alignment: .top, spacing: 16) {
            column(texts.compareThis, texts.compareRow(row: row, units: stats.units))
            column(texts.compareOther(duplicate: duplicate), texts.compareMatched(matched: matched, units: stats.units))
        }
        .padding(.vertical, 4)
    }

    private func column(_ heading: any AppText, _ values: [any AppText]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(heading).font(.subheadline.weight(.semibold))
            ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                Text(value).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// The devices an import names that aren't linked to gear yet, a section each with what to do with it (and, to add it, the new item's
/// name, type, kind, mount and collections), and the offer to link the activity profile.
struct NewDevicesSections: View {
    let fields: NewDevicesFields
    let enabled: Bool

    var body: some View {
        let texts = ImportActivityTexts.shared
        let cards = fields.cards.elements(as: NewDeviceCard.self)
        if !cards.isEmpty {
            Section {
                Text(texts.newDevicesHelp).font(.footnote).foregroundStyle(.secondary)
            } header: {
                Text(texts.newDevicesTitle)
            }
            ForEach(cards, id: \.id) { card in
                NewDeviceSection(card: card, fields: fields, enabled: enabled)
            }
        }
        if let offer = fields.profileOffer {
            let options = offer.options.elements(as: GearOption.self)
            Section {
                AppPickerRow(
                    name: AppStrings.string(texts.profileLinkTitle(offer: offer)),
                    selection: Binding(
                        get: { offer.chosen?.value },
                        set: { uri in fields.eventSink(NewDeviceEvent.LinkProfile(uri: options.first { $0.uri.value == uri }?.uri)) }
                    ),
                    options: [nil] + options.map { Optional($0.uri.value) },
                    optionLabel: { uri in AppStrings.string(texts.profileLinkChoice(offer: offer, uri: uri)) }
                ) {
                    Text(texts.profileLinkTitle(offer: offer))
                }
                .disabled(!enabled)
            } footer: {
                Text(texts.profileLinkHelp)
            }
        }
    }
}

/// One new device: its name and what's known of it, Later / Add as gear / Not gear, and the new item's fields while adding it.
struct NewDeviceSection: View {
    let card: NewDeviceCard
    let fields: NewDevicesFields
    let enabled: Bool

    @State private var name: TextFieldModel

    init(card: NewDeviceCard, fields: NewDevicesFields, enabled: Bool) {
        self.card = card
        self.fields = fields
        self.enabled = enabled
        _name = State(initialValue: TextFieldModel(card.name))
    }

    var body: some View {
        let texts = ImportActivityTexts.shared
        let send = fields.eventSink
        let id = card.id
        Section {
            VStack(alignment: .leading, spacing: 2) {
                Text(texts.deviceName(card: card)).font(.headline)
                if let about = texts.deviceAbout(card: card) {
                    Text(about).font(.footnote).foregroundStyle(.secondary)
                }
            }
            Picker(selection: Binding(get: { card.decision }, set: { send(NewDeviceEvent.Decide(id: id, decision: $0)) })) {
                ForEach(texts.deviceDecisions, id: \.decision) { choice in
                    Text(texts.decision(decision: choice.decision)).tag(choice.decision)
                }
            } label: {
                Text(texts.decision(decision: card.decision))
            }
            .pickerStyle(.segmented)
            if card.decision == .Add {
                AppTextField(label: texts.deviceNameLabel, model: name, state: card.name, layout: .row)
                AppPickerRow(
                    name: AppStrings.string(texts.deviceTypeLabel),
                    selection: Binding(get: { card.type }, set: { send(NewDeviceEvent.TypeChanged(id: id, type: $0)) }),
                    options: texts.gearTypes,
                    optionLabel: { AppStrings.string(GearTexts.shared.type(type: $0)) }
                ) {
                    Text(texts.deviceTypeLabel)
                }
                let kinds = texts.gearKinds(type: card.type)
                if !kinds.isEmpty {
                    AppPickerRow(
                        name: AppStrings.string(texts.deviceKindLabel),
                        selection: Binding(get: { card.kind }, set: { send(NewDeviceEvent.KindChanged(id: id, kind: $0)) }),
                        options: [nil] + kinds.map { Optional($0) },
                        optionLabel: { kind in AppStrings.string(texts.deviceKindChoice(type: card.type, kind: kind)) }
                    ) {
                        Text(texts.deviceKindLabel)
                    }
                }
                let mounts = fields.mountOptions.elements(as: GearOption.self)
                if !mounts.isEmpty {
                    AppPickerRow(
                        name: AppStrings.string(texts.deviceMountLabel),
                        selection: Binding(
                            get: { card.mountOn?.value },
                            set: { uri in send(NewDeviceEvent.MountOn(id: id, uri: mounts.first { $0.uri.value == uri }?.uri)) }
                        ),
                        options: [nil] + mounts.map { Optional($0.uri.value) },
                        optionLabel: { uri in AppStrings.string(texts.deviceMountChoice(fields: fields, uri: uri)) }
                    ) {
                        Text(texts.deviceMountLabel)
                    }
                }
                let collections = fields.collections.elements(as: GearCollectionOption.self)
                if !collections.isEmpty {
                    let chosen = Set(card.collections.elements(as: String.self))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(texts.deviceCollectionsLabel).font(.footnote).foregroundStyle(.secondary)
                        FlowRow {
                            ForEach(collections, id: \.rkey) { collection in
                                let selected = chosen.contains(collection.rkey)
                                Button { send(NewDeviceEvent.ToggleCollection(id: id, rkey: collection.rkey)) } label: {
                                    Text(verbatim: collection.name).font(.subheadline)
                                }
                                .collectionChip(selected: selected)
                                .accessibilityAddTraits(selected ? .isSelected : [])
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .disabled(!enabled)
    }
}

extension View {
    @ViewBuilder fileprivate func collectionChip(selected: Bool) -> some View {
        if selected {
            buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.small)
        } else {
            buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.small)
        }
    }
}
