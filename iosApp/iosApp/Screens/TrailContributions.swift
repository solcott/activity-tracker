import SwiftUI

/// Keeping a segment's trail details true on its native page (segments/trail-detail-suggestions): the quick check after a recent ride,
/// the regular's missing details, and, over the page, a detail's sheet and the picker suggesting a value. Every word and choice is
/// Kotlin's (`TrailContributionTexts`, `TrailFields`), shared with the Compose page.

/// The quick check: each detail with Still right and It's changed, then Not now and All still right.
struct QuickCheckSection: View {
    let check: QuickCheck
    let send: (any TrailEvent) -> Void

    var body: some View {
        Section {
            ForEach(check.shownRows, id: \.key) { row in
                let value = AppStrings.string(row.value)
                HStack {
                    Text(row.value)
                    Spacer()
                    answer(.check, label: .trailAnswerStill(value), chosen: row.stillRight) {
                        send(TrailEvent.Answer(key: row.key, still: true))
                    }
                    answer(.close, label: .trailAnswerChanged(value), chosen: row.changed) {
                        send(TrailEvent.Answer(key: row.key, still: false))
                    }
                }
            }
            HStack {
                Spacer()
                AppTextButton(.trailQuickLater) { send(TrailEvent.NotNow.shared) }
                AppSecondaryButton(.trailQuickAll, small: true) { send(TrailEvent.AllStillRight.shared) }
            }
        } header: {
            VStack(alignment: .leading, spacing: 2) {
                Text(.trailQuickTitle).font(.headline).foregroundStyle(.primary)
                Text(.trailQuickBody).font(.subheadline).foregroundStyle(.secondary)
            }
            .textCase(nil)
        }
    }

    private func answer(_ icon: AppIcon, label: LocalizedStringResource, chosen: Bool, action: @escaping () -> Void) -> some View {
        AppIconButton(icon, label: label, filled: chosen, action: action)
            .tint(chosen ? .accentColor : .secondary)
            .buttonStyle(.borderless)
    }
}

/// The regular's ways to add details: in place of the cards on a segment with none ([invite]), the invitation, else under them "Add a
/// missing detail"; either is a menu of the details missing, each opening its picker.
struct MissingDetailsSection: View {
    let fields: TrailFields
    let invite: Bool

    var body: some View {
        let missing = fields.shownMissing
        if !missing.isEmpty {
            Section {
                if invite {
                    Text(.trailEmptyTitle)
                    Text(.trailEmptyBody).font(.subheadline).foregroundStyle(.secondary)
                }
                Menu {
                    ForEach(missing, id: \.key) { detail in
                        Button {
                            fields.eventSink(TrailEvent.Suggest(key: detail.key))
                        } label: {
                            Text(detail.label)
                        }
                    }
                } label: {
                    Label {
                        Text(.trailAddMissing)
                    } icon: {
                        Image(AppIcon.add)
                    }
                }
            } header: {
                if invite { Text(TrailTexts.shared.title).font(.headline).foregroundStyle(.primary).textCase(nil) }
            }
        }
    }
}

extension View {
    /// Shows `TrailFields`' detail sheet and picker over the page, each SwiftUI's own sheet, sending the close event on a swipe down.
    func trailSheets(_ fields: TrailFields) -> some View {
        sheet(isPresented: .shown(fields.sheet != nil) { fields.eventSink(TrailEvent.Close.shared) }) {
            if let sheet = fields.sheet {
                TrailSheetView(sheet: sheet, fields: fields)
            }
        }
        .sheet(
            isPresented: .shown(fields.picker != nil) { fields.eventSink(TrailEvent.ClosePicker.shared) }
        ) {
            if let picker = fields.picker {
                TrailPickerView(picker: picker, send: fields.eventSink)
            }
        }
    }
}

/// One detail's sheet at medium height: its value and source, Still Right and It's Changed, its open suggestions with agree and
/// disagree, and for a regular "Suggest a Different Value…".
struct TrailSheetView: View {
    let sheet: TrailSheet
    let fields: TrailFields

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        if let value = sheet.value { Text(value).font(.title2.weight(.semibold)) }
                        if let source = sheet.source { Text(source).font(.subheadline).foregroundStyle(.secondary) }
                    }
                    if sheet.canConfirm {
                        HStack(spacing: 8) {
                            answer(.trailStillRight, chosen: sheet.stillRight) { fields.eventSink(TrailEvent.Confirm(still: true)) }
                            answer(.trailChanged, chosen: sheet.changed) { fields.eventSink(TrailEvent.Confirm(still: false)) }
                        }
                    }
                } footer: {
                    if sheet.canConfirm { Text(.trailWasItRight) }
                }
                let suggestions = sheet.shownSuggestions
                if !suggestions.isEmpty {
                    Section {
                        ForEach(suggestions, id: \.uri) { row in SuggestionRowView(row: row, send: fields.eventSink) }
                    } header: {
                        Text(.trailSuggestedHeading)
                    } footer: {
                        Text(.trailVotesPrivate)
                    }
                }
                if let error = fields.error {
                    Section { ErrorMessage(error, layout: .row) }
                }
                if sheet.canSuggest {
                    Section {
                        Button {
                            fields.eventSink(TrailEvent.Suggest(key: sheet.key))
                        } label: {
                            Text(.trailSuggestDifferent)
                        }
                    }
                }
            }
            .navigationTitle(Text(sheet.label))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(.actionDone) { fields.eventSink(TrailEvent.Close.shared) }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func answer(_ title: LocalizedStringResource, chosen: Bool, action: @escaping () -> Void) -> some View {
        // The chosen answer is the prominent one; choosing it again takes it back.
        if chosen {
            Button(action: action) { Text(title).frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
        } else {
            Button(action: action) { Text(title).frame(maxWidth: .infinity) }.buttonStyle(.bordered)
        }
    }
}

/// One open suggestion: its value, who suggested it and its totals, with agree and disagree unless it's the viewer's own.
private struct SuggestionRowView: View {
    let row: SuggestionRow
    let send: (any TrailEvent) -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(row.value)
                Text(row.author).font(.subheadline).foregroundStyle(.secondary)
                Text(row.totals).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            if !row.own {
                vote(systemImage: "hand.thumbsup", chosen: row.agreed, label: .trailAgree) {
                    send(TrailEvent.Vote(uri: row.uri, up: true))
                }
                vote(systemImage: "hand.thumbsdown", chosen: row.disagreed, label: .trailDisagree) {
                    send(TrailEvent.Vote(uri: row.uri, up: false))
                }
            }
        }
    }

    private func vote(systemImage: String, chosen: Bool, label: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: chosen ? systemImage + ".fill" : systemImage).frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.borderless)
        .tint(chosen ? .accentColor : .secondary)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }
}

/// The picker suggesting a value: the value now, each value of the detail with the current one marked and unselectable, and Suggest.
struct TrailPickerView: View {
    let picker: TrailPicker
    let send: (any TrailEvent) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Array(picker.shownOptions.enumerated()), id: \.offset) { index, option in
                        Button {
                            send(TrailEvent.Pick(index: Int32(index)))
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(option.text).foregroundStyle(option.current ? Color.secondary : Color.primary)
                                    if option.suggested {
                                        Text(.trailSuggestAlready).font(.footnote).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                if option.current {
                                    Text(.trailSuggestCurrent).font(.footnote).foregroundStyle(.secondary)
                                } else if picker.selected.map { Int($0) } == index {
                                    Image(AppIcon.check).foregroundStyle(.tint)
                                }
                            }
                        }
                        .disabled(option.current)
                    }
                } header: {
                    if let current = picker.current { Text(current).textCase(nil) }
                } footer: {
                    Text(.trailSuggestPublic)
                }
            }
            .navigationTitle(Text(picker.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.actionCancel) { send(TrailEvent.ClosePicker.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.trailSuggestSend) { send(TrailEvent.Send.shared) }.disabled(!picker.canSend)
                }
            }
        }
    }
}
