import SwiftUI

/// The Trail details section (segments/trail-attributes) on the Segment and Trail pages: one section per card Kotlin groups the rows into
/// (`TrailTexts.groups`, through the state's `trailGroups`), titled Difficulty, Surface & width or Access & season, each row's label
/// leading and what the data says trailing, as the shared pages show them. A Website row is a link the system browser opens, only once
/// `TrailLink` finds an http(s) URL. Nothing when there are no groups.
struct TrailDetailsSection: View {
    let groups: [TrailDetailGroup]

    var body: some View {
        ForEach(Array(groups.enumerated()), id: \.offset) { index, group in
            Section {
                ForEach(Array(group.shownRows.enumerated()), id: \.offset) { _, row in
                    TrailDetailRow(row: row)
                }
            } header: {
                // The section's heading over the first card, as the shared page titles the cards.
                VStack(alignment: .leading, spacing: 4) {
                    if index == 0 {
                        Text(TrailTexts.shared.title).font(.headline).foregroundStyle(.primary).textCase(nil)
                    }
                    // The card's heading, darker and heavier than its rows' labels, as the shared cards draw it.
                    Text(group.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).textCase(nil)
                }
            }
        }
    }
}

/// One Trail details row: its label leading and what the data says trailing, a Website row opening its link.
private struct TrailDetailRow: View {
    let row: TrailDetail

    var body: some View {
        // Labels medium weight in the secondary colour, values regular in the primary one, as the shared rows read.
        if let url = TrailLink.url(row.link) {
            LabeledContent {
                Link(destination: url) { Text(row.value) }
            } label: {
                Text(row.label).fontWeight(.medium).foregroundStyle(.secondary)
            }
        } else {
            LabeledContent {
                Text(row.value).foregroundStyle(.primary).multilineTextAlignment(.trailing)
            } label: {
                Text(row.label).fontWeight(.medium).foregroundStyle(.secondary)
            }
        }
    }
}

/// Which links a Website row may open: an absolute http or https URL with a host, checked again on the device though Kotlin and the
/// AppView checked it, so a row never opens another scheme.
enum TrailLink {
    static func url(_ text: String?) -> URL? {
        guard let text, let url = URL(string: text), let scheme = url.scheme?.lowercased(),
            scheme == "https" || scheme == "http", url.host?.isEmpty == false
        else { return nil }
        return url
    }
}
