import SwiftUI

/// One post as a card (feed/posts, ui/ios-design-system), for any native page that lists posts, drawn from Kotlin's choices for it
/// (`PostCardContent`, which the shared `PostCard` asks too): who reposted it, the author with its audience, the text, the embed (a
/// proposal with its place, name, status, totals and votes; a quote's quoted post one level deep, or "Post unavailable"; or the line for
/// an embed this version can't show), then Like and Repost on someone else's post, Quote on a listed one, and the counts.
///
/// It is a list row. Tapping it opens the post, which is also its VoiceOver action; the reposted-by and author lines, the place, the
/// quoted post and its link, the votes and the buttons take their own taps. What a tap does is the host's: the card sends the shared
/// `PostEvent`s. The menu offers Delete on the viewer's own quote; Report waits for the native report sheet (ios-native-post-card).
struct PostCardView: View {
    let content: PostCardContent
    let send: (any PostEvent) -> Void
    /// Given on the post page, where the counts list the post's quotes.
    var openQuotes: (() -> Void)? = nil

    private var post: PostView { content.post }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            repostedBy
            header
            if let text = content.text {
                Text(verbatim: text).font(.body)
            }
            proposal
            quoted
            if content.embedUnknown {
                Text(content.embedUnknownText()).font(.footnote).foregroundStyle(.secondary)
            }
            actions
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { send(PostEvent.OpenPost(post: post)) }
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: Text(PostCardContent.Companion.shared.open())) { send(PostEvent.OpenPost(post: post)) }
    }

    @ViewBuilder private var repostedBy: some View {
        if let line = content.repostedBy(), let reposter = content.reposter {
            Button { send(PostEvent.OpenReposter(reposter: reposter)) } label: {
                Label { Text(line) } icon: { Image(.repost) }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text(PostCardContent.Companion.shared.openReposter()))
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            // Plain, so the line keeps its colours rather than the accent's, and only it takes the tap.
            Button { send(PostEvent.OpenAuthor(post: post)) } label: {
                HStack(spacing: 8) {
                    Avatar(url: post.author.avatar, name: AppStrings.string(content.author()), size: 32)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(content.author()).font(.subheadline.weight(.semibold))
                        Text(content.audience()).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if content.offersDelete {
                Menu {
                    Button(role: .destructive) { send(content.menuEvent()) } label: { Text(content.menuItem()) }
                } label: {
                    Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                }
                .accessibilityLabel(Text(PostCardContent.Companion.shared.more()))
            }
        }
    }

    @ViewBuilder private var proposal: some View {
        if let proposes = content.proposes() {
            VStack(alignment: .leading, spacing: 8) {
                if let place = content.place() {
                    Button { send(PostEvent.Open(post: post)) } label: { Text(place).font(.footnote) }
                        .buttonStyle(.borderless)
                        .accessibilityHint(Text(PostCardContent.Companion.shared.openPlace()))
                }
                HStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(proposes).font(.headline)
                        if let status = content.proposalStatus() {
                            Text(status).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if content.canVote {
                        AppIconButton(.chevronUp, label: content.voteUp(), filled: content.votedUp) { send(content.vote(up: true)) }
                            .foregroundStyle(content.votedUp ? Color.accentColor : Color.secondary)
                        AppIconButton(.chevronDown, label: content.voteDown(), filled: content.votedDown) {
                            send(content.vote(up: false))
                        }
                        .foregroundStyle(content.votedDown ? Color.red : Color.secondary)
                    }
                }
            }
        }
    }

    @ViewBuilder private var quoted: some View {
        if let quoted = content.quoted {
            Button { send(PostEvent.OpenQuoted(uri: quoted.uri)) } label: {
                VStack(alignment: .leading, spacing: 4) {
                    if let header = content.quotedHeader() {
                        Text(header).font(.footnote).foregroundStyle(.secondary)
                    }
                    if let text = quoted.text {
                        Text(verbatim: text).font(.subheadline).lineLimit(6).fixedSize(horizontal: false, vertical: true).foregroundStyle(.primary)
                    }
                    if let proposes = content.quotedProposes() {
                        Text(proposes).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .quotedFrame()
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text(content.quotedOpen()))
            if let link = content.quotedLink {
                Button { send(PostEvent.OpenQuoted(uri: link)) } label: { Text(content.quotedLinkText()).font(.footnote) }
                    .buttonStyle(.borderless)
                    .accessibilityHint(Text(content.quotedOpen()))
            }
        } else if content.quotedUnavailable {
            Text(content.quotedUnavailableText())
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .quotedFrame()
        }
    }

    private var actions: some View {
        HStack(spacing: 0) {
            if content.offersLike {
                AppIconButton(.like, label: PostCardContent.Companion.shared.like(), filled: content.liked) {
                    send(PostEvent.Like(post: post))
                }
                .foregroundStyle(content.liked ? Color("LikeColor") : Color.accentColor)
            }
            if content.offersRepost {
                AppIconButton(.repost, label: content.repostLabel(), filled: content.reposted) { send(PostEvent.Repost(post: post)) }
                    .foregroundStyle(content.reposted ? Color("LikeColor") : Color.accentColor)
            }
            if content.offersQuote {
                AppIconButton(.quote, label: PostCardContent.Companion.shared.quote()) { send(PostEvent.Quote(post: post)) }
            }
            if let openQuotes {
                Button(action: openQuotes) { Text(content.counts()).font(.footnote).multilineTextAlignment(.leading) }
                    .buttonStyle(.borderless)
                    .accessibilityHint(Text(PostCardContent.Companion.shared.showQuotes()))
            } else {
                Text(content.counts()).font(.footnote)
            }
        }
    }
}

extension View {
    /// The rounded outline a quoted post sits in.
    fileprivate func quotedFrame() -> some View {
        overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Color(.separator)))
    }
}

#if DEBUG
    #Preview("Post cards") {
        List {
            ForEach(Samples.postCards.sorted { $0.key < $1.key }, id: \.key) { name, post in
                Section(name) {
                    PostCardView(content: PostCardContent.Companion.shared.of(post: post, viewerDid: Samples.postViewer, reason: nil)) { _ in }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
#endif
