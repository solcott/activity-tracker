import AppCommonApi
import IoGithubSolcottUistate
import SwiftUI

/// A list loaded page by page, laid out as ui/content-loading says, from a Kotlin `PagedState`: until the first answer, the first
/// page's progress or failure with Retry, centred as `ContentStateView` draws them; once answered, a grouped list of `top` (the
/// screen's own rows), a refresh's progress or failure, the items, `empty` when it lists nothing, and the next page's progress or
/// failure with Retry. What shows is Kotlin's `PagedShown`, the rule the shared paged list follows too.
///
/// Rows of the screen's own can follow the items (`after`: the viewer's own place on a board), above the empty message and the next
/// page's status, as the shared board orders them. A screen whose `top` rows must stay while a first page loads (a board's choices,
/// since a new choice starts its list again; a notice that explains why nothing loads) sets `keepsTop`: the list is then drawn with
/// `top` before the first answer too, and the first page's progress or failure follows it as a row.
///
/// It asks for the next page (`loadMore`) once the last item loaded is on screen and the state can load more, as the shared
/// `LoadMoreAtEnd` does. The items reach Swift untyped, since Swift export erases the state's type parameters, so the view names
/// their type, and `id` gives each one's identity.
struct PagedList<Item, Top: View, Row: View, After: View, Empty: View>: View {
    private let state: KotlinPagedState
    private let errorKind: UiError.Kind
    private let id: (Item) -> String
    private let retry: () -> Void
    private let loadMore: () -> Void
    private let top: Top
    private let row: (Item) -> Row
    private let after: After
    private let empty: Empty
    private let keepsTop: Bool

    /// The ids of the items on screen, which tell when the last one is.
    @State private var onScreen: Set<String> = []

    init(
        _ state: KotlinPagedState, of item: Item.Type = Item.self, errorKind: UiError.Kind, id: @escaping (Item) -> String,
        keepsTop: Bool = false, retry: @escaping () -> Void, loadMore: @escaping () -> Void, @ViewBuilder top: () -> Top,
        @ViewBuilder row: @escaping (Item) -> Row, @ViewBuilder after: () -> After, @ViewBuilder empty: () -> Empty
    ) {
        self.state = state
        self.errorKind = errorKind
        self.id = id
        self.keepsTop = keepsTop
        self.retry = retry
        self.loadMore = loadMore
        self.top = top()
        self.row = row
        self.after = after()
        self.empty = empty()
    }

    var body: some View {
        let shown = PagedShown.Companion.shared.of(state: state, errorKind: errorKind)
        if shown.firstPage && !keepsTop {
            firstPage(shown.below)
        } else if shown.firstPage {
            // The screen's own rows stay, and the first page's progress or failure follows them.
            List {
                top
                if let below = shown.below {
                    status(below, layout: .stacked, firstPage: true)
                }
            }
            .listStyle(.insetGrouped)
        } else {
            list(shown)
        }
    }

    @ViewBuilder private func firstPage(_ status: PagedShown.Status?) -> some View {
        switch status?.sealedType() {
        case .progress:
            AppProgress().frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .failure(failure):
            ErrorMessage(failure.value.error, retry: retry)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        case nil:
            // A list that isn't loading yet: nothing.
            Color.clear
        }
    }

    private func list(_ shown: PagedShown) -> some View {
        let items = state.items.compactMap { $0 as? Item }.map { Identified(id: id($0), item: $0) }
        let last = items.last?.id
        let wantsMore = state.canLoadMore && last.map(onScreen.contains) == true
        return List {
            top
            if let above = shown.above {
                status(above, layout: .row)
            }
            Section {
                ForEach(items, id: \.id) { identified in
                    row(identified.item)
                        .onAppear { onScreen.insert(identified.id) }
                        .onDisappear { onScreen.remove(identified.id) }
                }
            }
            after
            if shown.showsEmpty {
                empty.listStatusRow()
            }
            if let below = shown.below {
                status(below, layout: .stacked)
            }
        }
        .listStyle(.insetGrouped)
        .onChange(of: wantsMore, initial: true) { _, wants in
            if wants { loadMore() }
        }
    }

    /// An item with the identity `id` gives it, which `ForEach` needs as a key path.
    private struct Identified {
        let id: String
        let item: Item
    }

    @ViewBuilder private func status(_ status: PagedShown.Status, layout: ErrorMessage.Layout, firstPage: Bool = false) -> some View {
        switch status.sealedType() {
        case .progress:
            AppProgress(small: !firstPage).frame(maxWidth: .infinity).padding(.vertical, firstPage ? 24 : 0).listStatusRow()
        case let .failure(failure):
            ErrorMessage(failure.value.error, layout: layout, retry: retry).frame(maxWidth: .infinity).listStatusRow()
        }
    }
}

extension PagedList where After == EmptyView {
    /// A paged list with no rows after its items.
    init(
        _ state: KotlinPagedState, of item: Item.Type = Item.self, errorKind: UiError.Kind, id: @escaping (Item) -> String,
        keepsTop: Bool = false, retry: @escaping () -> Void, loadMore: @escaping () -> Void, @ViewBuilder top: () -> Top,
        @ViewBuilder row: @escaping (Item) -> Row, @ViewBuilder empty: () -> Empty
    ) {
        self.init(
            state, of: item, errorKind: errorKind, id: id, keepsTop: keepsTop, retry: retry, loadMore: loadMore, top: top, row: row,
            after: { EmptyView() }, empty: empty)
    }
}

extension View {
    /// A list's row that says how the list stands (progress, a failure, the empty message) rather than listing something: no
    /// background or separator, so it reads as part of the page.
    func listStatusRow() -> some View {
        listRowBackground(Color.clear).listRowSeparator(.hidden)
    }
}
