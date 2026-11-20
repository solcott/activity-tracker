import SwiftUI

/// The Trail details section (segments/trail-attributes) on the Segment and Trail pages: one row per attribute the served `trail` holds,
/// its label leading and what the data says trailing, in the words Kotlin chooses (`TrailTexts`, through the state's `trailDetails`),
/// as the shared pages show them. A Website row is a link the system browser opens, only once `TrailLink` finds an http(s) URL. Nothing
/// when there are no rows.
struct TrailDetailsSection: View {
    let rows: [TrailDetail]

    var body: some View {
        if !rows.isEmpty {
            Section {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    if let url = TrailLink.url(row.link) {
                        LabeledContent {
                            Link(destination: url) { Text(row.value) }
                        } label: {
                            Text(row.label)
                        }
                    } else {
                        LabeledContent {
                            Text(row.value).multilineTextAlignment(.trailing)
                        } label: {
                            Text(row.label)
                        }
                    }
                }
            } header: {
                Text(TrailTexts.shared.title)
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
