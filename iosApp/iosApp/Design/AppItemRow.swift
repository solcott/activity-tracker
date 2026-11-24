import SwiftUI

/// A row of a list of things rather than of settings (ui/ios-design-system): a picture, a title with lines under it, a summary of a
/// value, and, when tapping it opens something, the system's disclosure mark. A control at its trailing end (Unblock) stays outside
/// the row's own button and takes its own taps. It goes in the system's `List`; `AppListRow` is for settings' rows. A `figure` (a Trail
/// row's leader) stands at the end of the title's first line, its value over its caption; with one, a small `leading` mark (a rating
/// swatch) sits on the title's first line too, so a wrapped title and the lines under it start under the title.
struct AppItemRow<Leading: View, Trailing: View>: View {
    private let title: Text
    private let lines: [Text]
    private let summary: Text?
    private var figure: AppItemFigure?
    private var firstLine = false
    private let leading: Leading
    private let opens: (() -> Void)?
    private let disclosure: Bool
    private let trailing: Trailing

    /// A row with a control at its trailing end, such as a button; tapping the rest of it opens what `opens` opens.
    init(
        _ title: Text, lines: [Text] = [], summary: Text? = nil, opens: (() -> Void)? = nil, @ViewBuilder leading: () -> Leading,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.lines = lines
        self.summary = summary
        self.leading = leading()
        self.opens = opens
        disclosure = false
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: 12) {
            if let opens {
                // Plain, so it is this button alone that takes the taps on it: a list row's buttons in the default style would all fire.
                Button(action: opens) { content.contentShape(Rectangle()) }
                    .buttonStyle(.plain)
            } else {
                content
            }
            trailing
        }
    }

    private var content: some View {
        HStack(spacing: 12) {
            if firstLine {
                // The mark, title and figure share the title's first line; the disclosure mark stays centred on the row.
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    leading.alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + Self.markRaise }
                    texts
                    figure?.view
                }
                disclosureMark
            } else {
                leading
                texts
                HStack(spacing: 6) {
                    summary?.foregroundStyle(.secondary).lineLimit(1).fixedSize(horizontal: true, vertical: false)
                    disclosureMark
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// How far above the title's baseline a first-line mark is centred: half the body text's capital height, at the reader's text size.
    private static var markRaise: CGFloat { UIFont.preferredFont(forTextStyle: .body).capHeight / 2 }

    @ViewBuilder private var disclosureMark: some View {
        if disclosure {
            DisclosureMark()
        }
    }

    private var texts: some View {
        VStack(alignment: .leading, spacing: 2) {
            title.foregroundStyle(.primary)
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                line.font(.subheadline).foregroundStyle(.secondary)
            }
        }
        // The summary or figure keeps its width, and the title and its lines wrap in what is left, as the shared row lays them out.
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// This row laid out on its title's first line, with `figure` at its end when there is one (no leader, no figure).
    func figure(_ figure: AppItemFigure?) -> Self {
        var row = self
        row.figure = figure
        row.firstLine = true
        return row
    }
}

/// A figure at the end of an item row's first line (segments/path-page: a Trail row's leader): its value, heavier than the row's text and
/// in even-width digits so values line up down the list, over a caption cut short when long.
struct AppItemFigure {
    let value: Text
    let caption: Text?

    fileprivate var view: some View {
        CappedWidth(max: 112) {
            VStack(alignment: .trailing, spacing: 2) {
                value.font(.body.weight(.semibold)).monospacedDigit().foregroundStyle(.primary).lineLimit(1)
                caption?.font(.caption).foregroundStyle(.secondary).lineLimit(1).truncationMode(.tail)
            }
        }
    }
}

/// Its one subview at its own width, no wider than `max` (a frame's `maxWidth` would stretch it to `max`), so the row's text gets the rest.
private struct CappedWidth: Layout {
    let max: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let view = subviews.first else { return .zero }
        let width = Swift.min(view.sizeThatFits(.unspecified).width, max, proposal.width ?? .infinity)
        return CGSize(width: width, height: view.sizeThatFits(ProposedViewSize(width: width, height: nil)).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: bounds.origin, proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
    }
}

extension AppItemRow where Trailing == EmptyView {
    /// A row that opens something when tapped, with the disclosure mark, or one that only shows when `opens` is nil.
    init(
        _ title: Text, lines: [Text] = [], summary: Text? = nil, opens: (() -> Void)? = nil, @ViewBuilder leading: () -> Leading
    ) {
        self.title = title
        self.lines = lines
        self.summary = summary
        self.leading = leading()
        self.opens = opens
        disclosure = opens != nil
        trailing = EmptyView()
    }
}
