import SwiftUI
import Testing

@testable import ActivityTracker

/// The native post card in each case it draws differently, the post page, the quotes list and the quote sheet in each of their states,
/// against their references (ui/ios-design-system, feed/post-page, feed/quote-posts, dev/ios-snapshot-tests). The samples hold no picture
/// URLs that load, so avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct PostsSnapshotTests {
    nonisolated private static let cards = [
        "proposal", "voted", "votedDown", "adopted", "own", "pending", "quote", "quoteOfQuote", "unavailableQuote", "ownQuote", "unknown",
    ]
    nonisolated private static let reposts = ["repostedByOthers", "repostedByOne", "repostedByYou"]
    nonisolated private static let pages = ["loading", "failed", "loaded", "refreshing", "refreshFailed", "notFound", "reposters", "ownQuote"]
    nonisolated private static let pageVariants = [
        "likers", "actionFailed", "ownHidden", "replying", "moreReplies", "commentsFailed", "quoting", "confirmDelete",
    ]
    nonisolated private static let quotes = [
        "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "loadingMore", "pageFailed", "end", "notFound",
    ]
    nonisolated private static let sheets = ["open", "empty", "tooLong", "publicOnly", "failed", "posting"]

    private static let cardSize = CGSize(width: 393, height: 420)

    private func cardList(_ post: PostView, reason: ReasonRepost? = nil) -> some View {
        List {
            Section {
                PostCardView(content: PostCardContent.Companion.shared.of(post: post, viewerDid: Samples.postViewer, reason: reason)) { _ in }
            }
        }
        .listStyle(.insetGrouped)
    }

    @Test(arguments: cards)
    func postCard(sample: String) throws {
        let post = try #require(Samples.postCards[sample])
        assertAppSnapshot(of: cardList(post), size: Self.cardSize, named: sample)
    }

    @Test(arguments: reposts)
    func repostedPost(sample: String) throws {
        let item = try #require(Samples.postReposts[sample])
        assertAppSnapshot(of: cardList(item.post, reason: item.reason), size: Self.cardSize, named: sample)
    }

    @Test func aPostAtTheLargestTextSize() throws {
        let post = try #require(Samples.postCards["quote"])
        assertAppSnapshot(
            of: cardList(post).environment(\.dynamicTypeSize, .accessibility3), size: CGSize(width: 393, height: 1_200),
            named: "accessibility3")
    }

    @Test(arguments: pages + pageVariants)
    func postPage(sample: String) throws {
        let state = try #require(Samples.postPage[sample] ?? Samples.postPageVariants[sample])
        assertAppSnapshot(of: NavigationStack { PostPage(state: state) }, named: sample)
    }

    @Test(arguments: quotes)
    func postQuotes(sample: String) throws {
        let state = try #require(Samples.postQuotes[sample])
        assertAppSnapshot(of: NavigationStack { PostQuotesView(state: state) }, named: sample)
    }

    @Test(arguments: sheets)
    func quoteSheet(sample: String) throws {
        let fields = try #require(Samples.quoteSheet[sample])
        assertAppSnapshot(of: QuoteSheet(fields: fields), named: sample, precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.postCards.keys) == Set(Self.cards))
        #expect(Set(Samples.postReposts.keys) == Set(Self.reposts))
        #expect(Set(Samples.postPage.keys) == Set(Self.pages))
        #expect(Set(Samples.postPageVariants.keys) == Set(Self.pageVariants))
        #expect(Set(Samples.postQuotes.keys) == Set(Self.quotes))
        #expect(Set(Samples.quoteSheet.keys) == Set(Self.sheets))
    }
}
