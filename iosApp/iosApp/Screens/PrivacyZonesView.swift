import SwiftUI

/// The athlete's privacy zones (ui/settings-layout), drawn from the states `PrivacyZonesPresenter` publishes: each zone by name with its
/// radius, removed by a swipe; on a phone, a zone added where the athlete is; and how much of every route's start and end is hidden.
/// A zone's centre isn't shown. What it says and when it can change is Kotlin's (`ZoneTexts`, `PrivacyZonesTexts`, the state's
/// getters), shared with the Compose page. Each change saves at once.
struct PrivacyZonesView: View {
    let state: PrivacyZonesState

    @State private var label: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: PrivacyZonesState) {
        self.state = state
        _label = State(initialValue: TextFieldModel(state.label))
    }

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadPrivacyZones, retry: { send(PrivacyZonesEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(.settingsPrivacyZones), back: { send(PrivacyZonesEvent.Back.shared) })
    }

    private var form: some View {
        Form {
            if state.needsSignIn || state.error != nil {
                Section {
                    if state.needsSignIn { ErrorMessage(UiError(kind: .SignInAgain, reason: nil), layout: .row) }
                    if let error = state.error { ErrorMessage(error, layout: .row) }
                }
            }
            if let zones = state.zones?.elements(as: PrivacyZone.self) {
                Section {
                    if zones.isEmpty {
                        Text(.privacyZonesEmpty).foregroundStyle(.secondary)
                    }
                    ForEach(Array(zones.enumerated()), id: \.offset) { index, zone in
                        row(zone, at: index)
                    }
                } header: {
                    Text(.privacyZonesHelp).font(.subheadline.weight(.regular)).textCase(nil)
                } footer: {
                    Text(.privacyZonesFromActivity)
                }
                if state.canUseLocation { here }
                Section {
                    AppPickerRow(
                        String(localized: .privacyZonesHideEnds),
                        selection: Binding(get: { state.hideEndsMeters }, set: { send(PrivacyZonesEvent.HideEndsChanged(meters: $0)) }),
                        options: state.hideEndsChoices.elements(as: Int32.self),
                        optionLabel: distance)
                    .disabled(!state.enabled)
                } footer: {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(.privacyZonesHideEndsHelp)
                        Text(.privacyZonesOthersNote)
                    }
                }
            }
        }
    }

    /// A zone by its name, with "Radius" under it and its radius trailing as a menu; swiping it offers Remove.
    private func row(_ zone: PrivacyZone, at index: Int) -> some View {
        let name = AppStrings.string(ZoneTexts.shared.privacyZoneName(zone: zone, index: Int32(index)))
        return AppPickerRow(
            name: name,
            selection: Binding(
                get: { zone.radiusMeters }, set: { send(PrivacyZonesEvent.RadiusChanged(index: Int32(index), meters: $0)) }),
            options: state.choices.elements(as: Int32.self),
            optionLabel: distance
        ) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: name)
                Text(.privacyZonesRadius).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .disabled(!state.enabled)
        .swipeActions {
            if state.enabled {
                Button(role: .destructive) { send(PrivacyZonesEvent.Remove(index: Int32(index))) } label: {
                    Text(.privacyZonesRemove)
                }
            }
        }
    }

    /// On a phone: a label, and adding a zone where the athlete is. Without the location yet, it sends the permission's answer as the
    /// shared iOS page does: the location tracker asks the system when it starts.
    private var here: some View {
        Section {
            AppTextField(
                label: .privacyZonesLabel, model: label, state: state.label, layout: .row,
                options: TextFieldOptions(capitalization: .words))
                .disabled(!state.canAddHere)
            Button {
                send(state.hasLocationPermission ? PrivacyZonesEvent.AddAtCurrentLocation.shared : PrivacyZonesEvent.PermissionResult(granted: true))
            } label: {
                HStack(spacing: 8) {
                    Text(PrivacyZonesTexts.shared.addHere(locating: state.locating))
                    if state.locating { AppProgress(small: true) }
                }
            }
            .disabled(!state.canAddHere)
        }
    }

    private func distance(_ meters: Int32) -> String {
        AppStrings.string(PrivacyZonesTexts.shared.distance(meters: meters, units: stats.units))
    }

    private func send(_ event: any PrivacyZonesEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Privacy zones") {
        SamplePages(Samples.privacyZones) { state in NavigationStack { PrivacyZonesView(state: state) } }
    }

    #Preview("Privacy zones, beyond the shared samples") {
        SamplePages(Samples.privacyZonesVariants) { state in NavigationStack { PrivacyZonesView(state: state) } }
    }
#endif
