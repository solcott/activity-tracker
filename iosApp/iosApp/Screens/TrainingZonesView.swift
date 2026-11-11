import SwiftUI

/// The athlete's training zones (ui/settings-layout), drawn from the states `TrainingZonesPresenter` publishes: who can see them, the
/// threshold history, the default zones and each sport's own, and adding a sport. What it says and when each part shows is Kotlin's
/// (`TrainingZonesTexts`, `ZoneTexts`, `VisibilityTexts`, the state's getters), shared with the Compose page.
///
/// Alone it is titled in the stack's bar; beside Settings' list, in its own header. The default zones and a sport's zones it opens show
/// beside the list too, with a way back here.
struct TrainingZonesView: View {
    let state: TrainingZonesState

    var body: some View {
        ContentStateView(state.content, as: TrainingZones.self, errorKind: .LoadProfile, retry: { send(TrainingZonesEvent.Retry.shared) }) {
            zones in
            List {
                status
                content(zones)
            }
        }
        .pageTitle(Text(.zonesTitle), back: { send(TrainingZonesEvent.Back.shared) })
    }

    /// A failed or running refresh, and why saving the visibility failed.
    @ViewBuilder private var status: some View {
        RefreshStatusView(state: state.content, errorKind: .LoadProfile) { send(TrainingZonesEvent.Retry.shared) }.listStatusRow()
        if let error = state.error {
            ErrorMessage(error, layout: .row).listStatusRow()
        }
    }

    @ViewBuilder private func content(_ zones: TrainingZones) -> some View {
        if zones.hasZones {
            Section {
                AppPickerRow(
                    String(localized: .visibilityLabel),
                    selection: Binding(get: { state.visibility }, set: { send(TrainingZonesEvent.VisibilityChanged(visibility: $0)) }),
                    options: state.visibilityOptions.elements(as: String.self),
                    optionLabel: { AppStrings.string(LabelText.shared.visibility(visibility: $0)) }
                )
                .disabled(state.savingVisibility)
            } header: {
                Text(.zonesVisibilityHeading)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(.zonesVisibilityHelp)
                    if let note = VisibilityTexts.shared.followersNote(selected: state.visibility, approvesFollowers: zones.approvesFollowers) {
                        Text(note)
                    }
                }
            }
        }
        if zones.hasHistory {
            Section {
                AppListRow(Text(.zonesHistory), opens: { send(TrainingZonesEvent.History.shared) })
            }
        }
        summary(zones.defaults) { send(TrainingZonesEvent.EditDefaults.shared) }
        ForEach(zones.sports.elements(as: ZoneSummary.self), id: \.sport) { sport in
            if let name = sport.sport {
                summary(sport) { send(TrainingZonesEvent.EditSport(sport: name)) }
            }
        }
        let addable = zones.addableSports.elements(as: String.self)
        if !addable.isEmpty {
            Section {
                Menu {
                    ForEach(addable, id: \.self) { sport in
                        Button(Labels.sport(sport)) { send(TrainingZonesEvent.EditSport(sport: sport)) }
                    }
                } label: {
                    Text(.zonesAddSport)
                }
            } footer: {
                Text(.zonesSportsHelp)
            }
        }
    }

    /// One set of zones: its heading with Edit, its thresholds, then each kind of zone it offers (pace zones where offered, heart rate,
    /// power where offered), and what it applies to under it.
    private func summary(_ summary: ZoneSummary, edit: @escaping () -> Void) -> some View {
        Section {
            ForEach(Array(TrainingZonesTexts.shared.thresholds(summary: summary).enumerated()), id: \.offset) { _, line in
                LabeledContent {
                    Text(line.value).foregroundStyle(line.muted ? AnyShapeStyle(.secondary) : AnyShapeStyle(.primary))
                } label: {
                    Text(line.label)
                }
            }
            if summary.offers(kind: .Speed) {
                kind(.Speed, model: summary.speedModel, zones: summary.speedZones?.elements(as: Zone.self), paceUnit: summary.paceUnit)
            }
            kind(.HeartRate, model: summary.heartRateModel, zones: summary.heartRateZones?.elements(as: Zone.self), paceUnit: summary.paceUnit)
            if summary.offers(kind: .Power) {
                kind(.Power, model: summary.powerModel, zones: summary.powerZones?.elements(as: Zone.self), paceUnit: summary.paceUnit)
            }
        } header: {
            HStack {
                Text(TrainingZonesTexts.shared.heading(summary: summary)).accessibilityAddTraits(.isHeader)
                Spacer()
                Button(.actionEdit, action: edit).textCase(nil)
            }
        } footer: {
            Text(TrainingZonesTexts.shared.help(summary: summary))
        }
    }

    /// A kind of zone's heading with the model it comes from, then its zones.
    @ViewBuilder private func kind(_ kind: ZoneKind, model: ZoneModel, zones: [Zone]?, paceUnit: PaceUnit) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(ZoneTexts.shared.heading(kind: kind)).font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                Text(ZoneTexts.shared.model(model: model)).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
            }
            ZoneRows.basis(model)
        }
        .accessibilityElement(children: .combine)
        ZoneRows.zones(zones, model: model, kind: kind, paceUnit: paceUnit)
    }

    private func send(_ event: any TrainingZonesEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Training zones") {
        SamplePages(Samples.trainingZones) { state in NavigationStack { TrainingZonesView(state: state) } }
    }

    #Preview("Training zones, beyond the shared samples") {
        SamplePages(Samples.trainingZonesVariants) { state in NavigationStack { TrainingZonesView(state: state) } }
    }
#endif
