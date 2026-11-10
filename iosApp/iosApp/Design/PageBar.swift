import SwiftUI

extension View {
    /// The page's title (ui/ios-design-system): in the stack's own bar when the page is its stack's destination, and in a header at the
    /// top of its column (`PageBar`) when it is drawn beside another screen, under a bar that isn't its own (`\.inPane`). A page's
    /// navigation title never shows in a column: the bar over both columns keeps the title of the screen it belongs to (the roadmap's
    /// phase 4 probes, settings pages). A native page sets this in place of `.navigationTitle`.
    ///
    /// `back` is the page's own way back, as the shared top bar's `onBack` is, for a page that can be opened deeper than the column's
    /// first (`\.paneRoot`): its header shows a back button for it there. A page that is only ever a column's first page needs none.
    /// `canGoBack` false, as for a running leave, shows no back button anywhere: neither the header's nor, alone, the stack's, whose
    /// edge swipe goes with it.
    func pageTitle(_ title: Text, back: (() -> Void)? = nil, canGoBack: Bool = true) -> some View {
        modifier(PageTitle(title: title, back: canGoBack ? back : nil, canGoBack: canGoBack, actions: nil))
    }

    /// The page's title with its `actions` (such as Add, or a menu) at the trailing end of the stack's bar alone, and of its header
    /// in a column.
    func pageTitle(
        _ title: Text, back: (() -> Void)? = nil, canGoBack: Bool = true, @ViewBuilder actions: () -> some View
    ) -> some View {
        modifier(PageTitle(title: title, back: canGoBack ? back : nil, canGoBack: canGoBack, actions: AnyView(actions())))
    }
}

private struct PageTitle: ViewModifier {
    let title: Text
    let back: (() -> Void)?
    let canGoBack: Bool
    let actions: AnyView?
    @Environment(\.inPane) private var inPane
    @Environment(\.paneRoot) private var paneRoot

    func body(content: Content) -> some View {
        if inPane {
            // The first page of its column has no back button: the screen beside it is where to go back to, and it stays.
            content.pageBar { PageBar(title: title, back: paneRoot ? nil : back, actions: actions) }
        } else {
            content
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                // The stack's back button pops the Kotlin stack itself; a page that can't go back hides it, and its edge swipe with it.
                .navigationBarBackButtonHidden(!canGoBack)
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
/// A back button sits at its leading end and the page's actions at its trailing end, as a bar's do, and the title keeps clear of both.
struct PageBar: View {
    let title: Text
    var back: (() -> Void)?
    var actions: AnyView?

    var body: some View {
        title
            .font(.headline)
            .lineLimit(1)
            .accessibilityAddTraits(.isHeader)
            // Clear of whatever is at either end, the same on both sides, so the title stays centred over the column.
            .padding(.horizontal, actions != nil ? 104 : back != nil ? 52 : 16)
            .frame(maxWidth: .infinity, minHeight: 44)
            .overlay(alignment: .leading) {
                if let back {
                    Button(action: back) {
                        Image(systemName: "chevron.backward")
                            .font(.title3.weight(.semibold))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(Text(.actionBack))
                    .padding(.leading, 4)
                }
            }
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

    /// Whether the screen is the first page in its column beside another (`HostRecord.paneRoot`), which has no back button: the screen
    /// beside it is where to go back to. Only read in a column.
    @Entry var paneRoot = true
}
