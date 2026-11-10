import SwiftUI

extension View {
    /// The page's title (ui/ios-design-system): in the stack's own bar when the page is its stack's destination, and in a header at the
    /// top of its column (`PageBar`) when it is drawn beside another screen, under a bar that isn't its own (`\.inPane`). A page's
    /// navigation title never shows in a column: the bar over both columns keeps the title of the screen it belongs to (the roadmap's
    /// phase 4 probes, settings pages). A native page sets this in place of `.navigationTitle`.
    func pageTitle(_ title: Text) -> some View {
        modifier(PageTitle(title: title, actions: nil))
    }

    /// The page's title with its `actions` (such as Add, or a menu) at the trailing end of the stack's bar alone, and of its header
    /// in a column.
    func pageTitle(_ title: Text, @ViewBuilder actions: () -> some View) -> some View {
        modifier(PageTitle(title: title, actions: AnyView(actions())))
    }
}

private struct PageTitle: ViewModifier {
    let title: Text
    let actions: AnyView?
    @Environment(\.inPane) private var inPane

    func body(content: Content) -> some View {
        if inPane {
            // No back button: the page is the first of its column, and the screen beside it stays.
            content.pageBar { PageBar(title: title, actions: actions) }
        } else {
            content
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    if let actions {
                        ToolbarItemGroup(placement: .topBarTrailing) { actions }
                    }
                }
        }
    }
}

/// A page's title over its column, set as a navigation bar sets an inline title: centred, in the bar's weight, at least a bar's
/// height. The column's top safe area ends under the bar over both columns, so the header sits right beneath it.
/// The page's actions sit at its trailing end, as a bar's do, and the title keeps clear of them.
struct PageBar: View {
    let title: Text
    var actions: AnyView?

    var body: some View {
        title
            .font(.headline)
            .lineLimit(1)
            .accessibilityAddTraits(.isHeader)
            // Clear of the actions on both sides, so the title stays centred over the column.
            .padding(.horizontal, actions == nil ? 16 : 104)
            .frame(maxWidth: .infinity, minHeight: 44)
            .overlay(alignment: .trailing) {
                if let actions {
                    HStack(spacing: 4) { actions }.padding(.trailing, 8)
                }
            }
    }
}

extension View {
    /// Places `bar` over the top of this view's scrolling content, which scrolls under it as it does under the system's bar: with the
    /// scroll edge effect on iOS 26, and over the bar material with a hairline before it, as bars of those versions look.
    @ViewBuilder fileprivate func pageBar(@ViewBuilder _ bar: () -> some View) -> some View {
        if #available(iOS 26.0, *) {
            safeAreaBar(edge: .top, spacing: 0, content: bar)
        } else {
            safeAreaInset(edge: .top, spacing: 0) {
                bar()
                    .background { Rectangle().fill(.bar).ignoresSafeArea(edges: .top) }
                    .overlay(alignment: .bottom) { Divider() }
            }
        }
    }
}

extension EnvironmentValues {
    /// Whether the screen is drawn in a column beside another, under a navigation bar that belongs to that one (`SettingsColumns`).
    @Entry var inPane = false
}
