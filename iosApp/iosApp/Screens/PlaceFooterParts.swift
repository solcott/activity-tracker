import SwiftUI

/// A place's status (a segment's or a dive site's: proposed, duplicate, archived) in the tint, what it means under it, and Open the
/// original when it is a duplicate of one. Its words are Kotlin's (`PlaceTexts`), as the shared pages' are.
struct PlaceStatusHelp: View {
    let status: (any AppText)?
    let help: any AppText
    let offersOriginal: Bool
    let openOriginal: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let status {
                Text(status).font(.subheadline.weight(.semibold)).foregroundStyle(.tint)
            }
            Text(help).font(.footnote).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
        if offersOriginal {
            Button(action: openOriginal) { Text(PlaceTexts.shared.openOriginal) }
        }
    }
}

/// A segment's data credit as lines (`SegmentCreditTexts`): OpenStreetMap's first, linking to its copyright page when it does, then
/// the licence. `compact` for the box over a map, whose lines are a size smaller.
struct CreditLines: View {
    let credit: SegmentCredit
    var compact = false

    var body: some View {
        let links = SegmentCreditTexts.shared.links(credit: credit)
        ForEach(Array(SegmentCreditTexts.shared.lines(credit: credit).enumerated()), id: \.offset) { index, line in
            if index == 0, links, let url = URL(string: PlaceTexts.shared.OSM_COPYRIGHT_URL) {
                if compact {
                    Link(destination: url) { Text(line) }.font(.footnote)
                } else {
                    Link(destination: url) { Text(line) }
                }
            } else {
                Text(line).font(compact ? .caption : .footnote).foregroundStyle(.secondary)
            }
        }
    }
}
