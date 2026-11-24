import SwiftUI

/// A labelled photo covered until the viewer shows it (moderation/labels): the picture blurred behind a veil, the label's `reason` over
/// it with `show` under that, and a tap revealing it. Covered, the photo is neither seen nor reachable. `style` sizes it for where it is:
/// a full-screen gallery's page on black, a media strip's tile, or a small tile in a row of them. The caller sizes and clips it.
struct CoveredPhoto<Picture: View>: View {
    enum Style {
        /// A gallery page, on black in white type.
        case screen
        /// A tile in an activity's media strip.
        case tile
        /// A small square in a row of trail photos, the reason cut at three lines.
        case smallTile
    }

    private let reason: Text
    private let show: Text
    private let style: Style
    private let reveal: () -> Void
    private let picture: Picture

    init(reason: Text, show: Text, style: Style, reveal: @escaping () -> Void, @ViewBuilder picture: () -> Picture) {
        self.reason = reason
        self.show = show
        self.style = style
        self.reveal = reveal
        self.picture = picture()
    }

    var body: some View {
        if style == .screen {
            button.buttonStyle(.plain)
        } else {
            button.buttonStyle(.borderless)
        }
    }

    private var button: some View {
        Button(action: reveal) {
            ZStack {
                picture.blur(radius: blur).accessibilityHidden(true)
                veil
                words.padding(padding)
            }
        }
    }

    @ViewBuilder private var words: some View {
        let words = VStack(spacing: spacing) {
            reason.font(reasonFont).multilineTextAlignment(.center).lineLimit(style == .smallTile ? 3 : nil)
            show.font(showFont).foregroundStyle(.tint)
        }
        if style == .screen {
            words.foregroundStyle(.white)
        } else {
            words
        }
    }

    private var veil: Color {
        switch style {
        case .screen: Color.black.opacity(0.6)
        case .tile: Color(.secondarySystemBackground).opacity(0.85)
        case .smallTile: Color(.secondarySystemBackground).opacity(0.8)
        }
    }

    private var blur: CGFloat {
        switch style {
        case .screen: 40
        case .tile: 24
        case .smallTile: 16
        }
    }

    private var spacing: CGFloat {
        switch style {
        case .screen: 6
        case .tile: 4
        case .smallTile: 2
        }
    }

    private var padding: CGFloat {
        switch style {
        case .screen: 24
        case .tile: 8
        case .smallTile: 4
        }
    }

    private var reasonFont: Font {
        switch style {
        case .screen: .headline
        case .tile: .subheadline.weight(.semibold)
        case .smallTile: .caption2.weight(.semibold)
        }
    }

    private var showFont: Font {
        switch style {
        case .screen: .subheadline
        case .tile: .footnote
        case .smallTile: .caption2
        }
    }
}
