import SwiftUI

/// The devices the athlete marked as not gear (ui/settings-layout), drawn from the states `IgnoredSensorsPresenter` publishes: each by
/// its maker and model, with the end of its serial or that it may match other units, and Un-ignore. The words are Kotlin's
/// (`LinkedSensorTexts`), shared with the Compose page and the gear page's linked devices. It opens full width over Settings.
struct IgnoredSensorsView: View {
    let state: IgnoredSensorsState

    var body: some View {
        // The devices are read from `sensors`, never from the content's erased value, which an empty list can't be read through.
        ContentStateView(state.content, errorKind: .LoadIgnoredSensors, retry: { send(IgnoredSensorsEvent.Retry.shared) }) {
            list(state.sensors.elements(as: LinkedSensor.self))
        }
        .pageTitle(Text(.gearIgnoredSensors))
    }

    private func list(_ sensors: [LinkedSensor]) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadIgnoredSensors) { send(IgnoredSensorsEvent.Retry.shared) }
                .listStatusRow()
            // Why the last un-ignore failed, over the devices, which stay.
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            if sensors.isEmpty {
                Text(.gearIgnoredEmpty).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .listStatusRow()
            } else {
                Section {
                    ForEach(sensors, id: \.id) { sensor in
                        AppItemRow(
                            Text(LinkedSensorTexts.shared.name(sensor: sensor)),
                            lines: LinkedSensorTexts.shared.detail(sensor: sensor).map { [Text($0)] } ?? []
                        ) {
                            EmptyView()
                        } trailing: {
                            AppSecondaryButton(.gearUnignore, small: true) { send(IgnoredSensorsEvent.Unignore(id: sensor.id)) }
                                .accessibilityLabel(Text(LinkedSensorTexts.shared.unignore(sensor: sensor)))
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func send(_ event: any IgnoredSensorsEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Ignored devices") {
        SamplePages(Samples.ignoredSensors) { state in NavigationStack { IgnoredSensorsView(state: state) } }
    }

    #Preview("Ignored devices, beyond loading") {
        SamplePages(Samples.ignoredSensorsVariants) { state in NavigationStack { IgnoredSensorsView(state: state) } }
    }
#endif
