import SwiftUI

/// A row of a list of things rather than of settings (ui/ios-design-system): a picture, a title with lines under it, a summary of a
/// value, and, when tapping it opens something, the system's disclosure mark. A control at its trailing end (Unblock) stays outside
/// the row's own button and takes its own taps. It goes in the system's `List`; `AppListRow` is for settings' rows.
struct AppItemRow<Leading: View, Trailing: View>: View {
    private let title: Text
    private let lines: [Text]
    private let summary: Text?
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
            leading
            VStack(alignment: .leading, spacing: 2) {
                title.foregroundStyle(.primary)
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    line.font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // The summary keeps its one line, and the title and its lines wrap in what is left, as the shared row lays them out.
            HStack(spacing: 6) {
                summary?.foregroundStyle(.secondary).lineLimit(1).fixedSize(horizontal: true, vertical: false)
                if disclosure {
                    Image(systemName: "chevron.forward")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                        .accessibilityHidden(true)
                }
            }
        }
        .accessibilityElement(children: .combine)
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
