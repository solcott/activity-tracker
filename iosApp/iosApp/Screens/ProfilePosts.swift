import SwiftUI

/// The profile's choice between the athlete's activities and their posts (feed/posts), under the actions while their lists show, in
/// `ProfileTexts.tab`'s words.
struct ProfileTabPicker: View {
    let state: ProfileState
    let send: (any ProfileEvent) -> Void

    var body: some View {
        if state.showsActivities {
            Picker(selection: Binding(get: { state.tab }, set: { send(ProfileEvent.SelectTab(tab: $0)) })) {
                ForEach([ProfileTab.Activities, ProfileTab.Posts], id: \.self) { tab in
                    Text(ProfileTexts.shared.tab(tab: tab)).tag(tab)
                }
            } label: {
                EmptyView()
            }
            .pickerStyle(.segmented)
            .listStatusRow()
        }
    }
}

/// The athlete's posts the viewer may see, newest first, as native post cards a page at a time, laid out by Kotlin's `PagedShown` as
/// the shared page's `pagedRefreshStatus` and `pagedFooter` lay them out: the first page's or a refresh's progress or failure, the cards,
/// the empty message, and the next page's progress or failure. It asks for the next page once the last card shows. None while the viewer
/// blocks the athlete.
struct ProfilePosts: View {
    let state: ProfileState
    let send: (any ProfileEvent) -> Void
    @State private var lastShown = false

    var body: some View {
        if state.showsActivities {
            let shown = PagedShown.Companion.shared.of(state: state.posts, errorKind: .LoadPosts)
            let posts = state.posts.items.compactMap { $0 as? PostView }
            if let above = shown.above {
                status(above)
            }
            ForEach(posts, id: \.uri.value) { post in
                Section {
                    PostCardView(
                        content: PostCardContent.Companion.shared.of(post: post, viewerDid: state.viewerDid, reason: nil),
                        send: { send(ProfileEvent.Post(event: $0)) }, canReport: state.reporting.available)
                }
                .onAppear { if post.uri.value == posts.last?.uri.value { lastShown = true } }
                .onDisappear { if post.uri.value == posts.last?.uri.value { lastShown = false } }
            }
            if shown.showsEmpty {
                Text(ProfileTexts.shared.postsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity).listStatusRow()
            }
            if let below = shown.below {
                status(below)
            }
            Color.clear.frame(height: 0).listStatusRow()
                .onChange(of: lastShown && state.posts.canLoadMore, initial: true) { _, wants in
                    if wants { send(ProfileEvent.LoadMorePosts.shared) }
                }
        }
    }

    @ViewBuilder private func status(_ status: PagedShown.Status) -> some View {
        switch status.sealedType() {
        case .progress:
            AppProgress(small: true).frame(maxWidth: .infinity).listStatusRow()
        case let .failure(failure):
            ErrorMessage(failure.value.error, layout: .row) { send(ProfileEvent.RetryPosts.shared) }.frame(maxWidth: .infinity).listStatusRow()
        }
    }
}
