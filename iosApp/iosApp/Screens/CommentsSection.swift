import SwiftUI

/// A subject's comments (feed/comments) as sections of a native `List`, in the order and words of the shared list, all Kotlin's
/// (`CommentsTexts.rows`): the count, each thread's comment or "Comment unavailable" with its replies indented under it, the viewer's
/// just-sent replies and comments, "View n more replies", hidden comments dimmed with their note, Reply, and a menu whose Delete and
/// Block author ask first; then the next page's progress or failure and the field, saying which comment it answers.
///
/// It asks for the next page once a row of one of the last five threads, a just-sent comment or the rows after them shows, as the shared
/// list does. Report waits for the native report sheet (ios-native-post-card), so the menus leave it out.
struct CommentsSection: View {
    let state: CommentsState
    @State private var draft: TextFieldModel
    @State private var onScreen: Set<String> = []
    @State private var confirming: Confirmation?

    init(state: CommentsState) {
        self.state = state
        _draft = State(initialValue: TextFieldModel(state.draft))
    }

    private struct Confirmation {
        let item: CommentMenuItem
        let author: any AppText
    }

    var body: some View {
        let rows = CommentsTexts.shared.rows(state: state, reportAvailable: false).elements(as: CommentRow.self)
        let near = nearKeys(rows)
        let wantsMore = state.paging.canLoadMore && !onScreen.isDisjoint(with: near)
        Section {
            ForEach(rows, id: \.key) { row in
                CommentRowView(row: row, send: send, choose: choose)
                    .padding(.leading, row.isReply ? 32 : 0)
                    .onAppear { onScreen.insert(row.key) }
                    .onDisappear { onScreen.remove(row.key) }
            }
            loading
                .onAppear { onScreen.insert(Self.tailKey) }
                .onDisappear { onScreen.remove(Self.tailKey) }
        } header: {
            HStack {
                Text(CommentsTexts.shared.title)
                Spacer()
                Text(CommentsTexts.shared.count(state: state))
            }
        }
        .onChange(of: wantsMore, initial: true) { _, wants in
            if wants { send(CommentEvent.LoadMore.shared) }
        }
        .alert(
            confirmTitle, isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }), presenting: confirming
        ) { confirmation in
            Button(role: .destructive) {
                if let event = confirmation.item.event() { send(event) }
            } label: {
                Text(confirmation.item.action == .Delete ? CommentsTexts.shared.label(action: .Delete) : CommentsTexts.shared.blockConfirm)
            }
            Button(role: .cancel) {} label: { Text(CommentsTexts.shared.cancel) }
        } message: { confirmation in
            Text(confirmation.item.action == .Delete ? CommentsTexts.shared.deleteMessage : CommentsTexts.shared.blockMessage)
        }
        Section {
            field
        }
    }

    private var confirmTitle: Text {
        guard let confirming else { return Text(verbatim: "") }
        return confirming.item.action == .Delete
            ? Text(CommentsTexts.shared.deleteTitle) : Text(CommentsTexts.shared.blockTitle(author: confirming.author))
    }

    @ViewBuilder private var loading: some View {
        if let failure = CommentsTexts.shared.loadFailure(state: state) {
            ErrorMessage(failure, layout: .row) { send(CommentEvent.Retry.shared) }.listStatusRow()
        } else if CommentsTexts.shared.loading(state: state) {
            AppProgress(small: true).frame(maxWidth: .infinity).listStatusRow()
        }
    }

    private var field: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let replying = CommentsTexts.shared.replyingTo(state: state) {
                HStack {
                    Text(replying).font(.footnote).foregroundStyle(.secondary)
                    Spacer()
                    AppIconButton(.close, label: CommentsTexts.shared.cancelReply) { send(CommentEvent.CancelReply.shared) }
                }
            }
            HStack(spacing: 4) {
                AppTextField(
                    label: CommentsTexts.shared.placeholder, model: draft, state: state.draft, layout: .bare,
                    hint: Text(CommentsTexts.shared.placeholder), onSubmit: { send(CommentEvent.Send.shared) })
                AppIconButton(.send, label: CommentsTexts.shared.send) { send(CommentEvent.Send.shared) }
            }
            if let error = state.error {
                ErrorMessage(error, layout: .row)
            }
        }
    }

    /// The rows whose showing asks for the next page: those of the last five threads listed, and the just-sent comments after them.
    private func nearKeys(_ rows: [CommentRow]) -> Set<String> {
        let threads = state.paging.items.elements(as: InteractionsPackage.CommentThread.self).suffix(5).map { "thread/\($0.uri.value)|" }
        var keys: Set<String> = [Self.tailKey]
        for row in rows where row.key.hasPrefix("sent/") || threads.contains(where: { row.key.hasPrefix($0) }) {
            keys.insert(row.key)
        }
        return keys
    }

    private static let tailKey = "comments/loading"

    private func choose(_ item: CommentMenuItem, author: any AppText) {
        if item.confirms {
            confirming = Confirmation(item: item, author: author)
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
                Text(author).font(.subheadline.weight(.semibold))
                notices
                if let text = row.text {
                    Text(verbatim: text)
                }
                if let note = row.hiddenNote {
                    Text(note).font(.caption).foregroundStyle(.secondary)
                }
                if row.offersReply {
                    Button { send(CommentEvent.ReplyTo(uri: row.uri)) } label: { Text(CommentsTexts.shared.label(action: .Reply)).font(.footnote) }
                        .buttonStyle(.borderless)
                }
            }
            .opacity(row.hidden ? 0.55 : 1)
            .frame(maxWidth: .infinity, alignment: .leading)
            menu(author)
        }
    }

    @ViewBuilder private var notices: some View {
        let notices = row.notices.elements(as: NoticeText.self)
        ForEach(Array(notices.enumerated()), id: \.offset) { _, notice in
            Text(notice.text)
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .foregroundStyle(notice.isError ? Color.red : Color.primary)
                .background(
                    notice.isError ? Color.red.opacity(0.15) : Color(.secondarySystemFill),
                    in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
    }

    @ViewBuilder private func menu(_ author: any AppText) -> some View {
        // Report waits for the native report sheet; `rows` already leaves it out.
        let items = row.menu.elements(as: CommentMenuItem.self).filter { $0.action != .Report }
        if !items.isEmpty {
            Menu {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button(role: item.destructive ? .destructive : nil) { choose(item, author) } label: { Text(item.label) }
                }
            } label: {
                Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(Text(CommentsTexts.shared.more))
        }
    }
}
