import SwiftUI

/// A post's page (feed/post-page), drawn from the states `PostPagePresenter` publishes: the post's native card, a failed action, Show
/// likes and Show reposts with who liked and reposted it, Show quotes, and its comments (`CommentsSection`). A post the AppView doesn't
/// serve the viewer says it isn't available. The quote sheet and the delete confirmation are the page's `QuoteFields`. Every word and
/// condition is Kotlin's (`PostPageTexts`, `PostPage`'s getters, `PostCardContent`, `CommentsTexts`), shared with the Compose page.
/// Named `PostPage`, so Kotlin's loaded page is `LoadedPostPage` here.
struct PostPage: View {
    let state: PostPageState

    var body: some View {
        Group {
            if state.notFound {
                Text(PostPageTexts.shared.unavailable)
                    .foregroundStyle(.secondary)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else {
                ContentStateView(state.content, errorKind: .LoadPost, retry: retry) {
                    if let page = state.page {
                        list(page)
                    }
                }
            }
        }
        .pageTitle(Text(PostPageTexts.shared.title))
        .quoteSheet(state.page?.quote)
    }

    private func list(_ page: LoadedPostPage) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadPost, retry: retry).listStatusRow()
            Section {
                PostCardView(
                    content: PostCardContent.Companion.shared.of(post: page.post, viewerDid: page.viewerDid.value, reason: nil),
                    send: { send(PostPageEvent.Post(event: $0)) },
                    openQuotes: { send(PostPageEvent.OpenQuotes.shared) })
            }
            if let error = page.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            if page.showsLikesToggle || page.showsRepostsToggle || page.showsQuotes {
                Section {
                    if page.showsLikesToggle {
                        toggle(PostPageTexts.shared.likesToggle(shown: page.likesList != nil)) { send(PostPageEvent.ToggleLikesList.shared) }
                        if let likers = page.likesList { people(likers) }
                    }
                    if page.showsRepostsToggle {
                        toggle(PostPageTexts.shared.repostsToggle(shown: page.repostsList != nil)) {
                            send(PostPageEvent.ToggleRepostsList.shared)
                        }
                        if let reposters = page.repostsList { people(reposters) }
                    }
                    if page.showsQuotes {
                        AppListRow(Text(PostPageTexts.shared.showQuotes)) { send(PostPageEvent.OpenQuotes.shared) }
                    }
                }
            }
            CommentsSection(state: page.comments)
        }
        .listStyle(.insetGrouped)
    }

    private func toggle(_ label: any AppText, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(label) }
    }

    /// Who liked or reposted it: progress, why it couldn't load, the empty message, or a row per athlete opening their profile.
    @ViewBuilder private func people(_ list: LikesList) -> some View {
        let athletes = list.athletes.elements(as: ProfileViewBasic.self)
        if list.isLoading {
            AppProgress(small: true).frame(maxWidth: .infinity)
        } else if let error = list.error {
            ErrorMessage(error, layout: .row)
        } else if athletes.isEmpty {
            Text(PostPageTexts.shared.noLikes).foregroundStyle(.secondary)
        } else {
            ForEach(athletes, id: \.did.value) { athlete in
                let name = AppPackage.getShownName(athlete)
                AppItemRow(
                    Text(verbatim: name), lines: [Text(LabelText.shared.handle(handle: athlete.handle.value))],
                    opens: { send(PostPageEvent.OpenProfile(did: athlete.did)) }
                ) {
                    Avatar(url: athlete.avatar, name: name)
                }
            }
        }
    }

    private func retry() {
        send(PostPageEvent.Retry.shared)
    }

    private func send(_ event: any PostPageEvent) {
        state.eventSink(event)
    }
}

/// A post's quotes (feed/quote-posts) as native post cards, newest first, a page at a time, with the empty message, and "This post isn't
/// available" once the viewer may no longer see the quoted post. A failed action shows over them.
struct PostQuotesView: View {
    let state: PostQuotesState

    var body: some View {
        Group {
            if state.notFound {
                Text(PostPageTexts.shared.unavailable)
                    .foregroundStyle(.secondary)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else {
                PagedList(
                    state.quotes, of: PostView.self, errorKind: .LoadQuotes, id: { $0.uri.value },
                    // A card a section, as every list of cards is.
                    sections: { posts in posts.map { PagedSection(id: $0.uri.value, items: [$0]) } },
                    retry: { send(PostQuotesEvent.Retry.shared) }, loadMore: { send(PostQuotesEvent.LoadMore.shared) }
                ) {
                    if let error = state.error {
                        ErrorMessage(error, layout: .row).listStatusRow()
                    }
                } row: { post in
                    PostCardView(
                        content: PostCardContent.Companion.shared.of(post: post, viewerDid: state.viewerDid, reason: nil),
                        send: { send(PostQuotesEvent.Post(event: $0)) })
                } empty: {
                    Text(PostPageTexts.shared.quotesEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                }
            }
        }
        .pageTitle(Text(PostPageTexts.shared.quotesTitle))
        .quoteSheet(state.quote)
    }

    private func send(_ event: any PostQuotesEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Post page") {
        SamplePages(Samples.postPage) { state in NavigationStack { PostPage(state: state) } }
    }

    #Preview("Post page, beyond loading") {
        SamplePages(Samples.postPageVariants) { state in NavigationStack { PostPage(state: state) } }
    }

    #Preview("Quotes") {
        SamplePages(Samples.postQuotes) { state in NavigationStack { PostQuotesView(state: state) } }
    }
#endif
