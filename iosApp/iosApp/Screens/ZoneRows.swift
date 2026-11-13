import SwiftUI

/// The rows the zones pages share (ui/settings-layout): a kind of zone's model and what it is based on, then its zones, or what the
/// model needs to work them out. What each says is Kotlin's (`ZoneTexts`), shared with the Compose pages.
enum ZoneRows {
    /// A computed zone: its label in the tint, its name, and its range trailing, on one line when they fit; otherwise the range goes
    /// under the name at the trailing edge, so neither a name nor a range breaks inside a word.
    struct ZoneRow: View {
        let zone: Zone
        let kind: ZoneKind
        let paceUnit: PaceUnit

        var body: some View {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    label
                    name.fixedSize()
                    Spacer(minLength: 0)
                    range.fixedSize()
                }
                VStack(alignment: .trailing, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        label
                        name.frame(maxWidth: .infinity, alignment: .leading)
                    }
                    range
                }
            }
            .accessibilityElement(children: .combine)
        }

        private var label: some View {
            Text(ZoneTexts.shared.label(zone: zone))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tint)
                .frame(minWidth: 56, alignment: .leading)
        }

        private var name: some View { Text(ZoneTexts.shared.name(zone: zone)) }

        private var range: some View {
            Text(ZoneTexts.shared.range(zone: zone, kind: kind, paceUnit: paceUnit)).monospacedDigit().multilineTextAlignment(.trailing)
        }
    }

    /// `zones` as rows, or, while the model can't work them out (nil), what it needs.
    @ViewBuilder static func zones(_ zones: [Zone]?, model: ZoneModel, kind: ZoneKind, paceUnit: PaceUnit) -> some View {
        if let zones {
            ForEach(zones, id: \.label) { zone in ZoneRow(zone: zone, kind: kind, paceUnit: paceUnit) }
        } else {
            Text(ZoneTexts.shared.missing(basis: model.basis)).foregroundStyle(.secondary)
        }
    }

    /// A model's name with what it is based on under it, as a picker's label or a heading's line.
    static func basis(_ model: ZoneModel) -> some View {
        Text(ZoneTexts.shared.basis(basis: model.basis)).font(.footnote).foregroundStyle(.secondary)
    }

    /// A choice of `models` for one kind of zone, by token, each named as Kotlin names it, with what the chosen one is based on under
    /// the label. Choosing sends `onPick` with the token.
    static func modelPicker(
        model: ZoneModel, models: [ZoneModel], enabled: Bool, onPick: @escaping (String) -> Void
    ) -> some View {
        AppPickerRow(
            name: String(localized: .zonesModel),
            selection: Binding(get: { model.token }, set: onPick),
            options: models.map(\.token),
            optionLabel: { token in AppStrings.string(ZoneTexts.shared.modelChoice(token: token)) }
        ) {
            VStack(alignment: .leading, spacing: 2) {
                Text(.zonesModel)
                basis(model)
            }
        }
        .disabled(!enabled)
    }
}
