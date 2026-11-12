import SwiftUI

/// Moderation (moderation/private-subjects), drawn from the states `ModerationPresenter` publishes: a reported subject's AT-URI from the
/// labeler's queue, Open, and what the AppView answered. The subject shows who may see it, then the activity as the native card (inert,
/// as the shared page's is: no author link, Like or photo opening), or the comment's author and text, or that it isn't under review.
/// What it says that follows a rule, and when Open is offered, is Kotlin's (`ModerationSubjectTexts`), shared with the Compose page.
///
/// Settings offers it only off mobile (ui/settings-layout); it is native so that every screen the iOS host can show has a view.
struct ModerationView: View {
    let state: ModerationState
    /// The URI as typed. It starts on the state's field and the field moves it when the presenter replaces that.
    @State private var uri: TextFieldModel

    init(state: ModerationState) {
        self.state = state
        _uri = State(initialValue: TextFieldModel(state.uri))
    }

    var body: some View {
        List {
            Section {
                AppTextField(
                    label: .moderationUri, model: uri, state: state.uri,
                    options: TextFieldOptions(keyboard: .URL, capitalization: .never, autocorrection: false, submitLabel: .go),
                    hint: Text(.moderationUriPlaceholder), onSubmit: open)
            } footer: {
                Text(.moderationHelp)
            }
            Section {
                HStack(spacing: 12) {
                    AppButton(.moderationOpen, action: open)
                        .disabled(!ModerationSubjectTexts.shared.canOpen(state: state))
                    if state.loading {
                        ProgressView()
                    }
                }
                if let error = state.error {
                    ErrorMessage(error, layout: .row)
                }
            }
            if let subject = state.subject {
                self.subject(subject)
            }
        }
        .listStyle(.insetGrouped)
        .pageTitle(Text(.moderationTitle), back: { state.eventSink(ModerationEvent.Back.shared) })
    }

    @ViewBuilder private func subject(_ subject: any ModerationSubject) -> some View {
        if let shown = subject as? ModerationSubject.Activity {
            Section {
                ActivityCardView(
                    content: ActivityCardContent(activity: shown.activity, isOwn: false), authorClickable: false, likeable: false,
                    send: { _ in })
            } header: {
                Text(ModerationSubjectTexts.shared.visibility(visibility: shown.visibility))
            }
        } else if let shown = subject as? ModerationSubject.Comment {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(ModerationSubjectTexts.shared.commentAuthor(comment: shown.comment)).font(.subheadline.weight(.semibold))
                    Text(verbatim: shown.comment.text)
                }
                .padding(.vertical, 4)
                .accessibilityElement(children: .combine)
            } header: {
                Text(ModerationSubjectTexts.shared.visibility(visibility: shown.visibility))
            }
        } else {
            Section {
                Text(.moderationNotUnderReview)
            }
        }
    }

    private func open() {
        state.eventSink(ModerationEvent.Open.shared)
    }
}

#if DEBUG
    #Preview("Moderation") {
        SamplePages(Samples.moderation.merging(Samples.moderationVariants) { first, _ in first }) { state in
            NavigationStack { ModerationView(state: state) }
        }
    }
#endif
