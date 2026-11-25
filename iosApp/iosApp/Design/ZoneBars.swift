import SwiftUI

/// Time in zones as one thin bar, each zone's share in its colour (`ZoneColors`), lowest first, as the shared `ZoneStrip` draws it.
struct ZoneStripBar: View {
    let rows: [ZoneRow]

    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 1) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    let fraction = CGFloat(row.fraction)
                    if fraction > 0 {
                        Rectangle()
                            .fill(ZoneColors.color(row.tone))
                            .frame(width: max(1, geometry.size.width * fraction - 1))
                    }
                }
            }
        }
        .frame(height: 4)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

/// Time in heart rate or power zones, hottest first as the shared `ZoneBars` lists them: each zone's label, name and range over a bar of
/// its share in the zone's colour (`ZoneColors`), its time and percent at the end; each read as Kotlin's sentence.
struct ZoneTimesView: View {
    let heading: any AppText
    let rows: [ZoneRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(heading).font(.subheadline.weight(.semibold))
            ForEach(Array(rows.enumerated().reversed()), id: \.offset) { _, row in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(row.label).font(.footnote.weight(.semibold))
                        Text(row.name).font(.footnote)
                        Text(row.range).font(.caption).foregroundStyle(.secondary)
                        Spacer(minLength: 8)
                        Text(row.time).font(.footnote.weight(.medium)).monospacedDigit()
                        Text(row.percent).font(.caption).foregroundStyle(.secondary).monospacedDigit()
                    }
                    GeometryReader { geometry in
                        Capsule().fill(Color(.secondarySystemFill))
                            .overlay(alignment: .leading) {
                                Capsule().fill(ZoneColors.color(row.tone))
                                    .frame(width: max(4, geometry.size.width * row.fraction))
                            }
                    }
                    .frame(height: 6)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(row.spoken))
            }
        }
        .padding(.vertical, 4)
    }
}
