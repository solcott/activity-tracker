import SwiftUI

/// A subject's comments (feed/comments) as sections of a native `List`, in the order and words of the shared list, all Kotlin's
/// (`CommentsTexts.rows`): the count, each thread's comment or "Comment unavailable" with its replies indented under it, the viewer's
/// just-sent replies and comments, "View n more replies", hidden comments dimmed with their note, Reply, and a menu whose Delete and
/// Block author ask first; then the next page's progress or failure. The field is `CommentComposer`, at the Comments screen's foot.
///
/// It asks for the next page once a row of one of the last five threads, a just-sent comment or the rows after them shows, as the shared
/// list does. Given the page's `Reporting`, others' comments offer Report where a labeler is configured, sending the comment's
/// `ReportingEvent.Open` that Kotlin builds (`Reporting.reportComment`); the page shows the sheet (`reporting(_:)`). Without one they
/// offer no Report.
struct CommentsSection: View {
    let state: CommentsState
    let reporting: Reporting?
    @State private var onScreen: Set<String> = []
    @State private var confirming: Confirmation?

    init(state: CommentsState, reporting: Reporting? = nil) {
        self.state = state
        self.reporting = reporting
    }

    /// A chosen item that asks first, with what it asks (`CommentsTexts.confirmation`).
    private struct Confirmation {
        let item: CommentMenuItem
        let texts: CommentConfirmation
    }

    var body: some View {
        let rows = CommentsTexts.shared.rows(state: state, reportAvailable: reporting?.available ?? false).elements(as: CommentRow.self)
        let wantsMore = CommentsTexts.shared.wantsMore(shown: Array(onScreen), state: state)
        Section {
            ForEach(rows, id: \.key) { row in
                CommentRowView(row: row, send: send) { item, author in choose(item, row: row, author: author) }
                    .padding(.leading, row.isReply ? 32 : 0)
                    .onAppear { onScreen.insert(row.key) }
                    .onDisappear { onScreen.remove(row.key) }
            }
            loading
                .onAppear { onScreen.insert(CommentsTexts.shared.LOADING_KEY) }
                .onDisappear { onScreen.remove(CommentsTexts.shared.LOADING_KEY) }
        } header: {
            // The screen's bar already says they're comments. At a grouped list's usual header size: the first header under a bar would
            // otherwise draw large.
            Text(CommentsTexts.shared.count(state: state)).font(.footnote)
        }
        .onChange(of: wantsMore, initial: true) { _, wants in
            if wants { send(CommentEvent.LoadMore.shared) }
        }
        .alert(
            confirmTitle, isPresented: .shown(confirming != nil) { confirming = nil }, presenting: confirming
        ) { confirmation in
            Button(role: .destructive) {
                if let event = confirmation.item.event() { send(event) }
            } label: {
                Text(confirmation.texts.confirm)
            }
            Button(role: .cancel) {
            } label: {
                Text(CommentsTexts.shared.cancel)
            }
        } message: { confirmation in
            Text(confirmation.texts.message)
        }
    }

    private var confirmTitle: Text {
        guard let confirming else { return Text(verbatim: "") }
        return Text(confirming.texts.title)
    }

    @ViewBuilder private var loading: some View {
        if let failure = CommentsTexts.shared.loadFailure(state: state) {
            ErrorMessage(failure, layout: .row) { send(CommentEvent.Retry.shared) }.listStatusRow()
        } else if CommentsTexts.shared.loading(state: state) {
            AppProgress(small: true).frame(maxWidth: .infinity).listStatusRow()
        }
    }

    /// The author as the report sheet names them, as the shared list does: their display name, else their `@handle`.
    private static func reportName(_ author: ProfileViewBasic) -> String {
        AppStrings.string(LabelText.shared.shownName(account: author))
    }

    private func choose(_ item: CommentMenuItem, row: CommentRow, author: any AppText) {
        if item.action == .Report {
            if let reporting, let comment = row.comment {
                reporting.eventSink(reporting.reportComment(comment: comment, authorName: Self.reportName(comment.author)))
            }
        } else if let texts = CommentsTexts.shared.confirmation(action: item.action, author: author) {
            confirming = Confirmation(item: item, texts: texts)
        } else if let event = item.event() {
            send(event)
        }
    }

    private func send(_ event: any CommentEvent) {
        state.eventSink(event)
    }
}

/// One row of the comment list: a comment with its author, notices, text, hidden note, Reply and menu; the placeholder for one that
/// isn't listed; or a thread's "View more replies", its progress or why its replies couldn't load.
struct CommentRowView: View {
    let row: CommentRow
    let send: (any CommentEvent) -> Void
    let choose: (CommentMenuItem, any AppText) -> Void

    var body: some View {
        switch row.kind {
        case .Unavailable:
            Text(CommentsTexts.shared.unavailable).italic().foregroundStyle(.secondary)
        case .MoreReplies:
            more
        default:
            comment
        }
    }

    @ViewBuilder private var more: some View {
        let load = { send(CommentEvent.LoadMoreReplies(thread: row.uri)) }
        if row.repliesLoading {
            AppProgress(small: true)
        } else if let error = row.repliesError {
            ErrorMessage(error, layout: .row, retry: load)
        } else if let more = row.more {
            Button(action: load) { Text(more).font(.footnote) }.buttonStyle(.borderless)
        }
    }

    private var comment: some View {
        let author = row.author ?? AppText.Companion.shared.written(text: "")
        return HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                CommentAuthorLine(author: Text(author)) { row.posted(now: $0.now, zone: $0.kotlinZone) }
                notices
                if let text = row.text {
                    Text(verbatim: text)
                }
                if let note = row.hiddenNote {
                    Text(note).font(.caption).foregroundStyle(.secondary)
                }
                if row.offersReply || row.offersLike || row.likeCount > 0 {
                    HStack(spacing: 16) {
                        if row.offersReply {
                            Button {
                                send(CommentEvent.ReplyTo(uri: row.uri))
                            } label: {
                                Text(CommentsTexts.shared.label(action: .Reply)).font(.footnote)
                            }
                            .buttonStyle(.borderless)
                        }
                        likes
                    }
                }
            }
            .opacity(row.hidden ? 0.55 : 1)
            .frame(maxWidth: .infinity, alignment: .leading)
            menu(author)
        }
    }

    /// The like button (filled in the like colour once liked) and the count after it (feed/comment-likes); on the viewer's own comment,
    /// which can't be liked, the icon and count alone.
    @ViewBuilder private var likes: some View {
        let tint = row.liked ? Color("LikeColor") : Color.secondary
        HStack(spacing: 4) {
            if row.offersLike {
                Button {
                    send(CommentEvent.Like(uri: row.uri))
                } label: {
                    Image(.like, filled: row.liked).foregroundStyle(tint)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(row.likeLabel))
                .accessibilityAddTraits(row.liked ? .isSelected : [])
            } else if row.likeCount > 0 {
                Image(.like).foregroundStyle(tint).accessibilityHidden(true)
            }
            if let number = row.likeNumber {
                Text(verbatim: number).foregroundStyle(.secondary).accessibilityLabel(Text(row.likeCountText))
            }
        }
        .font(.footnote)
    }

    @ViewBuilder private var notices: some View {
        let notices = row.notices.elements(as: NoticeText.self)
        ForEach(Array(notices.enumerated()), id: \.offset) { _, notice in
            NoticeChip(notice: notice)
        }
    }

    @ViewBuilder private func menu(_ author: any AppText) -> some View {
        let items = row.menu.elements(as: CommentMenuItem.self)
        if !items.isEmpty {
            Menu {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button(role: item.destructive ? .destructive : nil) {
                        choose(item, author)
                    } label: {
                        Text(item.label)
                    }
                }
            } label: {
                Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(Text(CommentsTexts.shared.more))
        }
    }
}

/// A comment's author and, after them, when it was written (`posted`, Kotlin's `CommentsTexts.posted`: "5 minutes ago" today, "Yesterday at
/// 6:12 PM" before), on the environment's clock, so previews and snapshots fix it. The name gives way first.
struct CommentAuthorLine: View {
    let author: Text
    let posted: (ShownClock) -> (any AppText)?
    @Environment(\.clock) private var clock

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            author.font(.subheadline.weight(.semibold)).lineLimit(1)
            if let posted = posted(clock) {
                clock.text(posted).font(.footnote).foregroundStyle(.secondary).lineLimit(1).layoutPriority(1)
            }
        }
    }
}
