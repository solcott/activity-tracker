import SwiftUI

/// The Trail details section (segments/trail-attributes) on the Segment and Trail pages: one section per card Kotlin groups the rows into
/// (`TrailTexts.groups`, through the state's `trailGroups`), titled Difficulty, Surface & width or Access & season, each row's label
/// leading and what the data says trailing, as the shared pages show them. A Website row is a link the system browser opens, only once
/// `TrailLink` finds an http(s) URL. Nothing when there are no groups.
struct TrailDetailsSection: View {
    let groups: [TrailDetailGroup]
    /// Whether the headings are rows that scroll with the list rather than section headers: a plain list (the Trail page's) pins its
    /// section headers over the rows below them, so its headings are rows.
    var headingsAsRows = false
    /// "Riders confirmed these in Nov 2026", under the section's title (segments/trail-detail-suggestions).
    var lastConfirmed: (any AppText)?
    /// Opens a row's sheet, for a row that `opens`.
    var open: (String) -> Void = { _ in }
    /// Opens a segment a Trail page row lists as varying.
    var openSegment: (String) -> Void = { _ in }

    var body: some View {
        ForEach(Array(groups.enumerated()), id: \.offset) { index, group in
            if headingsAsRows {
                heading(group, first: index == 0)
                    .padding(.top, 8)
                    .listRowSeparator(.hidden)
                rows(group)
            } else {
                Section {
                    rows(group)
                } header: {
                    heading(group, first: index == 0)
                }
            }
        }
    }

    private func rows(_ group: TrailDetailGroup) -> some View {
        ForEach(Array(group.shownRows.enumerated()), id: \.offset) { _, row in
            TrailDetailRow(row: row, open: open)
            // On the Trail page, the segments riders changed this detail on (segments/trail-attributes).
            ForEach(row.shownVarying, id: \.uri) { segment in
                Button {
                    openSegment(segment.uri)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: segment.name)
                        Text(verbatim: "\(AppStrings.string(segment.value)) · \(AppStrings.string(segment.source))")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    .padding(.leading, 16)
                }
                .foregroundStyle(.primary)
            }
        }
    }

    /// The section's heading over the first card, as the shared page titles the cards, then the card's.
    private func heading(_ group: TrailDetailGroup, first: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if first {
                Text(TrailTexts.shared.title).font(.headline).foregroundStyle(.primary).textCase(nil)
                if let lastConfirmed {
                    Label {
                        Text(lastConfirmed)
                    } icon: {
                        Image(AppIcon.check)
                    }
                    .font(.footnote).foregroundStyle(.secondary).textCase(nil)
                }
            }
            // The card's heading, darker and heavier than its rows' labels, as the shared cards draw it.
            Text(group.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).textCase(nil)
        }
        .accessibilityAddTraits(.isHeader)
    }
}

/// One Trail details row: its label leading and what the data says trailing with where it came from under it (segments/trail-detail-suggestions),
/// a Website row opening its link, and a row a rider may change opening its sheet.
private struct TrailDetailRow: View {
    let row: TrailDetail
    let open: (String) -> Void

    var body: some View {
        // Labels medium weight in the secondary colour, values regular in the primary one, as the shared rows read.
        if let url = TrailLink.url(row.link) {
            LabeledContent {
                Link(destination: url) { Text(row.value) }
            } label: {
                label
            }
        } else if row.opens, let key = row.key {
            Button {
                open(key)
            } label: {
                LabeledContent {
                    HStack(spacing: 6) {
                        value
                        Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                } label: {
                    label
                }
            }
            .foregroundStyle(.primary)
        } else {
            LabeledContent {
                value
            } label: {
                label
            }
        }
    }

    private var label: some View {
        Text(row.label).fontWeight(.medium).foregroundStyle(.secondary)
    }

    private var value: some View {
        VStack(alignment: .trailing, spacing: 2) {
            Text(row.value)
                .italic(row.question)
                .foregroundStyle(row.question ? AnyShapeStyle(.secondary) : AnyShapeStyle(.primary))
                .multilineTextAlignment(.trailing)
            if let source = row.source {
                if row.community {
                    // A plain stack, not a Label: a Label in LabeledContent's value takes the list row's label style and leaves the
                    // rows after it blank.
                    HStack(spacing: 4) {
                        Image(systemName: "person.2.fill").accessibilityHidden(true)
                        Text(source)
                    }
                    .font(.footnote).foregroundStyle(.tint)
                } else {
                    Text(source).font(.footnote).foregroundStyle(.secondary)
                }
            }
            if let note = row.note {
                Text(note).font(.footnote.weight(.medium)).foregroundStyle(.tint)
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
