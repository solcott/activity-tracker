import SwiftUI

/// A subject's comments on a screen of their own (feed/comments), drawn from the states `CommentsPresenter` publishes: the count, the
/// threads a page at a time with their replies and actions (`CommentsSection`), and the field at the foot above the keyboard
/// (`CommentComposer`). Opened from "Add a comment" the field takes focus once; choosing Reply takes it again. Report on others'
/// comments opens the native report sheet from the screen's `Reporting` (`reporting(_:)`).
struct CommentsView: View {
    let state: CommentsScreenState
    @FocusState private var fieldFocused: Bool
    @State private var focusedOnOpen = false

    var body: some View {
        ContentStateView(state.content, errorKind: state.loadError, retry: { state.eventSink(CommentsScreenEvent.Retry.shared) }) {
            if let page = state.page {
                List {
                    RefreshStatusView(state: state.content, errorKind: state.loadError) {
                        state.eventSink(CommentsScreenEvent.Retry.shared)
                    }
                    .listStatusRow()
                    CommentsSection(state: page.comments, reporting: state.reporting)
                }
                .listStyle(.insetGrouped)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    CommentComposer(state: page.comments, focused: $fieldFocused)
                }
                .onChange(of: page.comments.replyingTo?.uri.value) { _, replying in
                    if replying != nil { fieldFocused = true }
                }
                .task {
                    // Once, as the screen first opens from "Add a comment"; not again on coming back to it.
                    if state.focus && !focusedOnOpen {
                        focusedOnOpen = true
                        fieldFocused = true
                    }
                }
            }
        }
        .pageTitle(Text(CommentsTexts.shared.title), subtitle: state.page?.subtitle.map { Text($0) })
        .reporting(state.reporting)
    }
}

/// The field to add a comment, at the Comments screen's foot over the bar material: which comment it answers with a way back to a
/// top-level one, the field and Send, and why the last send, delete, hide or block failed (feed/comments).
struct CommentComposer: View {
    let state: CommentsState
    let focused: FocusState<Bool>.Binding
    @State private var draft: TextFieldModel

    init(state: CommentsState, focused: FocusState<Bool>.Binding) {
        self.state = state
        self.focused = focused
        _draft = State(initialValue: TextFieldModel(state.draft))
    }

    var body: some View {
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
                    hint: Text(CommentsTexts.shared.placeholder), onSubmit: { send(CommentEvent.Send.shared) }
                )
                .focused(focused)
                AppIconButton(.send, label: CommentsTexts.shared.send) { send(CommentEvent.Send.shared) }
            }
            if let error = state.error {
                ErrorMessage(error, layout: .row)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(.bar)
        .overlay(alignment: .top) { Divider() }
    }

    private func send(_ event: any CommentEvent) {
        state.eventSink(event)
    }
}

/// A subject's comments in brief (feed/comments), rows of its page's social section under its counts row (`SocialCountsRow`, which has
/// the comment count): the first comments (two lines each), or "Loading comments…" by a spinner while they load, "View all" at the end
/// while there are more, and "Add a comment", shaped like a field but a button. Each opens the Comments screen.
struct CommentsPreviewRows: View {
    let preview: CommentsPreview

    var body: some View {
        let open = { preview.eventSink(CommentsPreviewEvent.OpenAll.shared) }
        if let loading = preview.loadingText {
            HStack(spacing: 8) {
                ProgressView()
                Text(loading).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
            .accessibilityElement(children: .combine)
        }
        ForEach(preview.rows.elements(as: CommentPreviewRow.self), id: \.key) { row in
            Button(action: open) {
                VStack(alignment: .leading, spacing: 2) {
                    CommentAuthorLine(author: Text(verbatim: row.author)) { row.posted(now: $0.now, zone: $0.kotlinZone) }
                    Text(verbatim: row.text).lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            // A comment reads as content, not a link: View all and Add a comment carry the tint.
            .buttonStyle(.plain)
        }
        if let viewAll = preview.viewAll {
            Button(action: open) { Text(viewAll).frame(maxWidth: .infinity, alignment: .trailing) }
                .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        }
        Button {
            preview.eventSink(CommentsPreviewEvent.Compose.shared)
        } label: {
            Text(preview.addComment)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

#if DEBUG
    #Preview("Comments") {
        SamplePages(Samples.comments) { state in NavigationStack { CommentsView(state: state) } }
    }

    #Preview("Comments, beyond loading") {
        SamplePages(Samples.commentsVariants) { state in NavigationStack { CommentsView(state: state) } }
    }
#endif
