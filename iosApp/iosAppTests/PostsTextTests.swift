import Testing

@testable import ActivityTracker

/// What the native post card, comment list and quote sheet say and decide, read from Swift through the catalog (ui/ios-design-system,
/// dev/shared-display-text, dev/ios-swift-interop).
@MainActor
struct PostsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private func card(_ name: String) throws -> PostCardContent {
        let post = try #require(Samples.postCards[name])
        return PostCardContent.Companion.shared.of(post: post, viewerDid: Samples.postViewer, reason: nil)
    }

    @Test func everyCardSampleReads() throws {
        for name in Samples.postCards.keys {
            let content = try card(name)
            #expect(!(read(content.author()) ?? "").isEmpty, "\(name) has no author")
            #expect(!(read(content.counts()) ?? "").isEmpty, "\(name) has no counts")
        }
    }

    @Test func aSharedProposal() throws {
        let content = try card("proposal")
        #expect(read(content.audience()) == "Followers")
        #expect(read(content.proposes()) == "Proposes “Wiggly Woods”")
        #expect(read(content.proposalStatus()) == "Open for votes · 4 up · 1 down")
        #expect(read(content.counts()) == "3 likes · 2 comments · 1 repost · 2 quotes")
        #expect(content.offersLike && content.liked && content.canVote)
    }

    @Test func quotes() throws {
        #expect(read(try card("quote").quotedHeader()) == "Bob · Followers")
        #expect(try card("quoteOfQuote").quotedLink != nil)
        let gone = try card("unavailableQuote")
        #expect(gone.quoted == nil && gone.quotedUnavailable)
        #expect(read(gone.quotedUnavailableText()) == "Post unavailable")
        #expect(try card("ownQuote").offersDelete)
    }

    @Test func repostedByYou() throws {
        let item = try #require(Samples.postReposts["repostedByYou"])
        let content = PostCardContent.Companion.shared.ofRepost(reposted: item, viewerDid: Samples.postViewer)
        #expect(read(content.repostedBy()) == "Reposted by you")
        #expect(content.reposted)
    }

    @Test func commentRowsReadInOrder() throws {
        let state = try #require(Samples.postPageVariants["moreReplies"]?.page)
        let rows = CommentsTexts.shared.rows(state: state.comments, reportAvailable: false).elements(as: CommentRow.self)
        #expect(rows.map(\.kind) == [.Comment, .Comment, .MoreReplies, .Unavailable, .Comment, .MoreReplies])
        #expect(read(rows[2].more) == "View 3 more replies")
        #expect(rows[5].repliesError != nil)
        #expect(read(CommentsTexts.shared.count(state: state.comments)) == "2 comments")
    }

    @Test func aHiddenCommentOnTheViewersOwnPost() throws {
        let state = try #require(Samples.postPageVariants["ownHidden"]?.page)
        let row = try #require(CommentsTexts.shared.rows(state: state.comments, reportAvailable: false).elements(as: CommentRow.self).first)
        #expect(row.hidden && !row.offersReply)
        #expect(read(row.hiddenNote) == "Hidden. Only you and its author can see it.")
        #expect(row.menu.elements(as: CommentMenuItem.self).map { read($0.label) } == ["Unhide", "Mute author", "Block author"])
    }

    @Test func theQuoteSheet() throws {
        let tooLong = try #require(Samples.quoteSheet["tooLong"])
        #expect(!tooLong.textValid && !tooLong.canPost)
        #expect(read(QuoteSheetTexts.shared.tooLong) == "Keep it under 1,000 characters.")
        let open = try #require(Samples.quoteSheet["open"])
        #expect(open.audiences.elements(as: String.self).map { read(QuoteSheetTexts.shared.audience(visibility: $0)) } == ["Followers", "Mutuals"])
        #expect(open.canPost)
    }

    @Test func thePostPageAndTheProfilesTabs() {
        #expect(read(PostPageTexts.shared.likesToggle(shown: false)) == "Show likes")
        #expect(read(PostPageTexts.shared.repostsToggle(shown: true)) == "Hide reposts")
        #expect(read(ProfileTexts.shared.tab(tab: .Posts)) == "Posts")
        #expect(read(ProfileTexts.shared.postsEmpty) == "No posts yet")
    }
}
