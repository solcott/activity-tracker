import SwiftUI

extension View {
    /// The theme's like colour (`LikeColor`, the shared theme's amber) while `on`, a liked or reposted state, else `off`.
    func likeTint(_ on: Bool, off: Color = .secondary) -> some View {
        foregroundStyle(on ? Color(.like) : off)
    }
}

/// Like, filled in the like colour once liked, as the shared UI's like button. On a card or a post it is an icon button in the accent
/// colour; `compact`, beside a comment's count, it is the bare icon in the secondary colour, marked selected once liked.
struct LikeButton: View {
    private let liked: Bool
    private let label: Text
    private let compact: Bool
    private let action: () -> Void

    init(liked: Bool, label: LocalizedStringResource, compact: Bool = false, action: @escaping () -> Void) {
        self.init(liked: liked, labelText: Text(label), compact: compact, action: action)
    }

    /// A like whose label Kotlin chooses (a post's, a comment's naming its author).
    init(liked: Bool, label: any AppText, compact: Bool = false, action: @escaping () -> Void) {
        self.init(liked: liked, labelText: Text(label), compact: compact, action: action)
    }

    private init(liked: Bool, labelText: Text, compact: Bool, action: @escaping () -> Void) {
        self.liked = liked
        self.label = labelText
        self.compact = compact
        self.action = action
    }

    var body: some View {
        if compact {
            Button(action: action) {
                Image(.like, filled: liked).likeTint(liked)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(label)
            .accessibilityAddTraits(liked ? .isSelected : [])
        } else {
            AppIconButton(.like, labelText: label, filled: liked, action: action).likeTint(liked, off: .accentColor)
        }
    }
}
