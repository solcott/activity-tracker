import SwiftUI

/// A page's counts on one row at the top of its social section (feed/comments, feed/post-page): likes first, then what else people did
/// with it, each an icon and a number, as the shared `SocialCountsRow` draws them. It is a list row; each count takes its own tap.
struct SocialCountsRow<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        HStack(spacing: 20) {
            content
            Spacer(minLength: 0)
        }
        // The hairline under it runs the section's width, as under the rows that follow, not from the first number.
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
    }
}

/// One count: `icon` and `count`, read as `label` ("3 likes"), its number in the accent colour when it opens something and secondary
/// otherwise. With `action` the whole of it is one button, whose hint is `actionLabel`
/// ("Show likes"). With `iconAction` the icon is a button of its own instead, named `iconLabel` (Like on someone else's activity), filled
/// in the like colour once `filled`, and the number alone takes `action`.
struct SocialCount: View {
    let icon: AppIcon
    let count: String
    let label: any AppText
    var actionLabel: (any AppText)?
    var action: (() -> Void)?
    var filled = false
    var iconLabel: (any AppText)?
    var iconAction: (() -> Void)?

    var body: some View {
        HStack(spacing: 6) {
            if let iconAction {
                Button(action: iconAction) { symbol }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(iconLabel.map { Text($0) } ?? Text(verbatim: ""))
                    .accessibilityAddTraits(filled ? .isSelected : [])
                number
            } else if let action {
                Button(action: action) {
                    HStack(spacing: 6) {
                        symbol
                        Text(verbatim: count)
                    }
                }
                .buttonStyle(.borderless)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(label))
                .accessibilityHint(actionLabel.map { Text($0) } ?? Text(verbatim: ""))
                .accessibilityAddTraits(.isButton)
            } else {
                HStack(spacing: 6) {
                    symbol
                    Text(verbatim: count).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(label))
            }
        }
        .font(.body.monospacedDigit())
    }

    private var symbol: some View {
        Image(icon, filled: filled)
            .foregroundStyle(filled ? Color("LikeColor") : Color.secondary)
            .accessibilityHidden(true)
    }

    /// The number after an icon button: a button of its own when the count opens something.
    @ViewBuilder private var number: some View {
        if let action {
            Button(action: action) { Text(verbatim: count) }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(label))
                .accessibilityHint(actionLabel.map { Text($0) } ?? Text(verbatim: ""))
        } else {
            Text(verbatim: count).foregroundStyle(.secondary).accessibilityLabel(Text(label))
        }
    }
}

/// The comment count, which opens the Comments screen.
struct CommentCount: View {
    let preview: CommentsPreview

    var body: some View {
        SocialCount(icon: .comment, count: preview.countNumber, label: preview.countText) {
            preview.eventSink(CommentsPreviewEvent.OpenAll.shared)
        }
    }
}

extension SocialCount {
    /// A count that is one button, or none without `action`.
    init(icon: AppIcon, count: String, label: any AppText, actionLabel: (any AppText)? = nil, action: (() -> Void)?) {
        self.icon = icon
        self.count = count
        self.label = label
        self.actionLabel = actionLabel
        self.action = action
    }
}
