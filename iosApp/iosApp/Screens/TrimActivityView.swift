import SwiftUI

/// Trimming an activity's start or end (feed/activity-trim), a grouped form over the states `TrimPresenter` publishes: the native map with
/// the cut dashed and the kept part over it (`TrimEditor.routeMap`), the profile with the cut parts shaded (`TrimTexts.profile`) over a
/// two-thumb slider whose thumbs read as "Trim start" and "Trim end", the Time | Distance choice, each end's readout (a tap opens its
/// exact value), the suggestion, the totals before and after and the efforts it removes. Trim (Done for an unsaved activity) is in the
/// bar; a saved activity's asks first. Every word is Kotlin's, shared with the Compose screen.
struct TrimActivityView: View {
    let state: TrimState

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadTrim, retry: { send(TrimEvent.Retry.shared) }) {
            if state.notSent {
                Text(.trimNotSent)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if let editor = state.editor {
                TrimForm(state: state, editor: editor)
            }
        }
        .pageTitle(Text(.trimTitle)) {
            if state.editor != nil {
                AppBarAction(state.draft ? .trimDone : .trimSave, busy: state.saving, enabled: state.canSave) {
                    send(TrimEvent.Save.shared)
                }
            }
        }
        .alert(Text(.trimConfirmTitle), isPresented: confirming) {
            Button(role: .destructive) {
                send(TrimEvent.Confirm.shared)
            } label: {
                Text(.trimSave)
            }
            Button(role: .cancel) {
                send(TrimEvent.CancelConfirm.shared)
            } label: {
                Text(.actionCancel)
            }
        } message: {
            if let editor = state.editor { Text(TrimTexts.shared.confirm(editor: editor)) }
        }
        .sheet(isPresented: exactOpen) {
            if let exact = state.exact, let editor = state.editor {
                TrimExactSheet(exact: exact, editor: editor, send: send)
            }
        }
    }

    private var confirming: Binding<Bool> {
        Binding(get: { state.confirming }, set: { if !$0 && state.confirming { send(TrimEvent.CancelConfirm.shared) } })
    }

    private var exactOpen: Binding<Bool> {
        Binding(get: { state.exact != nil }, set: { if !$0 && state.exact != nil { send(TrimEvent.CancelExact.shared) } })
    }

    private func send(_ event: any TrimEvent) {
        state.eventSink(event)
    }
}

/// The trim's form: the map, the profile and handles with the readouts, the suggestion, the totals and the notes.
private struct TrimForm: View {
    let state: TrimState
    let editor: TrimEditor
    @Environment(\.stats) private var stats
    /// The share of full speed a slowed handle follows at, while one does.
    @State private var scrub: Double?

    var body: some View {
        let texts = TrimTexts.shared
        let units = stats.units
        Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            if let map = editor.routeMap {
                Section {
                    RouteMapView(map: map, cut: editor.kept != nil).listRowInsets(EdgeInsets())
                }
            }
            Section {
                Picker(selection: byDistance) {
                    Text(.trimModeTime).tag(false)
                    Text(.trimModeDistance).tag(true)
                } label: {
                    EmptyView()
                }
                .pickerStyle(.segmented)
                .disabled(!editor.offersDistance)
                if let chart = texts.profile(editor: editor, units: units) {
                    LineChartView(chart: chart, interactive: false)
                }
                RangeSlider(
                    lower: Double(editor.from), upper: Double(editor.to), bounds: 0...Double(max(editor.elapsed, 1)),
                    lowerLabel: Text(.trimStartHandle), upperLabel: Text(.trimEndHandle),
                    value: { value in
                        Text(texts.handleValue(editor: editor, start: abs(value - Double(editor.from)) < 0.5, units: units))
                    },
                    precision: { texts.scrubFactor(offset: Double($0)) },
                    onPrecision: { scrub = $0 }
                ) { from, to in
                    state.eventSink(TrimEvent.Moved(from: Int32(from.rounded()), to: Int32(to.rounded())))
                }
                .frame(height: 44)
                .disabled(state.saving)
                // One line either way, so the form never moves while the handle slows: the hint, or how fast the handle follows.
                Group {
                    if let share = scrub, let speed = texts.scrubLabel(factor: share) {
                        Text(speed).foregroundStyle(Color.accentColor)
                    } else {
                        Text(.trimScrubHint).foregroundStyle(.secondary)
                    }
                }
                .font(.footnote)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                HStack(alignment: .top) {
                    TrimEnd(editor: editor, start: true, send: state.eventSink)
                    Spacer()
                    TrimEnd(editor: editor, start: false, send: state.eventSink)
                }
                .disabled(state.saving)
                if editor.changed {
                    Button {
                        state.eventSink(TrimEvent.Reset.shared)
                    } label: {
                        Text(.trimReset)
                    }
                }
            } footer: {
                if !editor.offersDistance { Text(.trimDistanceUnavailable) }
            }
            if let suggestion = editor.suggestion, let text = texts.suggestion(suggestion: suggestion, units: units) {
                Section {
                    Text(text)
                    if editor.suggestionApplied {
                        Button {
                            state.eventSink(TrimEvent.Reset.shared)
                        } label: {
                            Text(.trimSuggestUndo)
                        }
                    } else {
                        Button {
                            state.eventSink(TrimEvent.ApplySuggestion.shared)
                        } label: {
                            Text(.trimSuggestApply)
                        }
                    }
                } header: {
                    Text(.trimSuggestTitle)
                }
            }
            Section {
                ForEach(Array(texts.totals(editor: editor, units: units).enumerated()), id: \.offset) { _, row in
                    LabeledContent {
                        VStack(alignment: .trailing) {
                            Text(row.value).font(.headline)
                            if let was = row.was { Text(was).font(.footnote).foregroundStyle(.secondary) }
                        }
                    } label: {
                        Text(row.label)
                    }
                }
            } footer: {
                VStack(alignment: .leading) {
                    ForEach(Array(texts.effortNotes(editor: editor).enumerated()), id: \.offset) { _, note in
                        Text(note).foregroundStyle(.red)
                    }
                }
            }
        }
    }

    private var byDistance: Binding<Bool> {
        Binding(get: { editor.byDistance }, set: { state.eventSink(TrimEvent.ModeChanged(byDistance: $0)) })
    }
}

/// One end: its readout, which opens its exact value, over the buttons that nudge it earlier or later; holding one repeats it, and the
/// steps grow the longer it is held (`TrimTexts.nudgeSeconds`).
private struct TrimEnd: View {
    let editor: TrimEditor
    let start: Bool
    let send: (any TrimEvent) -> Void
    @Environment(\.stats) private var stats

    var body: some View {
        VStack(alignment: start ? .leading : .trailing, spacing: 4) {
            TrimReadout(editor: editor, start: start) { send(TrimEvent.EditExact(start: start, units: stats.units)) }
            HStack(spacing: 16) {
                ForEach([false, true], id: \.self) { later in
                    AppRepeatIconButton(
                        later ? .chevron : .chevronBack, label: TrimTexts.shared.nudgeLabel(start: start, later: later)
                    ) { count in
                        send(TrimEvent.Nudge(start: start, later: later, repeat: count, units: stats.units))
                    }
                    .font(.title3)
                }
            }
        }
    }
}

/// One end's readout: where it lands, its clock time and what it cuts, opening its exact value.
private struct TrimReadout: View {
    let editor: TrimEditor
    let start: Bool
    let open: () -> Void
    @Environment(\.stats) private var stats

    var body: some View {
        let texts = TrimTexts.shared
        Button(action: open) {
            VStack(alignment: start ? .leading : .trailing, spacing: 2) {
                Text(start ? .trimStart : .trimEnd).font(.caption).foregroundStyle(.secondary)
                Text(texts.position(editor: editor, start: start, units: stats.units)).font(.headline)
                Text(texts.clock(editor: editor, start: start)).font(.footnote)
                Text(texts.cut(editor: editor, start: start, units: stats.units)).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

/// The exact value of one end, typed or stepped earlier or later.
private struct TrimExactSheet: View {
    let exact: TrimExactValue
    let editor: TrimEditor
    let send: (any TrimEvent) -> Void
    @State private var model: TextFieldModel
    @Environment(\.stats) private var stats

    init(exact: TrimExactValue, editor: TrimEditor, send: @escaping (any TrimEvent) -> Void) {
        self.exact = exact
        self.editor = editor
        self.send = send
        _model = State(initialValue: TextFieldModel(exact.field))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    AppTextField(
                        label: TrimTexts.shared.exactLabel(editor: editor, units: stats.units), model: model, state: exact.field,
                        supportingText: exact.invalid ? String(localized: .trimExactInvalid) : nil, isError: exact.invalid,
                        onSubmit: { send(TrimEvent.ApplyExact(units: stats.units)) }
                    )
                }
                Section {
                    HStack {
                        Button {
                            send(TrimEvent.StepExact(later: false, units: stats.units))
                        } label: {
                            Text(.trimExactEarlier)
                        }
                        .buttonStyle(.bordered)
                        Spacer()
                        Button {
                            send(TrimEvent.StepExact(later: true, units: stats.units))
                        } label: {
                            Text(.trimExactLater)
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            .navigationTitle(Text(exact.start ? .trimStart : .trimEnd))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        send(TrimEvent.CancelExact.shared)
                    } label: {
                        Text(.actionCancel)
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        send(TrimEvent.ApplyExact(units: stats.units))
                    } label: {
                        Text(.trimExactSet)
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#if DEBUG
    #Preview("Trim") {
        SamplePages(Samples.trim) { state in NavigationStack { TrimActivityView(state: state) } }
    }
#endif
