import SwiftUI

/// A full-screen gallery's bar on black (an activity's photos, a trail's): Close, and at the other end the position ("1 of 16") when
/// there is more than one photo and, while `showsMenu`, the photo's menu of `menu`'s items. Its words are `MediaGalleryTexts`'.
struct GalleryChrome<MenuItems: View>: View {
    private let counter: (any AppText)?
    private let showsMenu: Bool
    private let close: () -> Void
    private let menu: MenuItems

    init(counter: (any AppText)?, showsMenu: Bool, close: @escaping () -> Void, @ViewBuilder menu: () -> MenuItems) {
        self.counter = counter
        self.showsMenu = showsMenu
        self.close = close
        self.menu = menu()
    }

    var body: some View {
        let texts = MediaGalleryTexts.shared
        HStack(spacing: 12) {
            Button(action: close) {
                AppIconLabel(.close)
            }
            .accessibilityLabel(Text(texts.close))
            Spacer(minLength: 8)
            if let counter {
                Text(counter).font(.subheadline.monospacedDigit())
            }
            if showsMenu {
                Menu {
                    menu
                } label: {
                    AppIconLabel(.more)
                }
                .accessibilityLabel(Text(texts.more))
            }
        }
        .foregroundStyle(.white)
    }
}
