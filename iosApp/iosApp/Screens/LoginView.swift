import SwiftUI

/// Sign-in (auth/sign-in), drawn from the states `LoginPresenter` publishes: the app's first native screen (ui/ios-native-shell).
struct LoginView: View {
    let state: LoginState
    /// The handle as typed, held here so Sign in can follow it. It starts on the state's field and the field moves it when the
    /// presenter replaces that, as it does when a session ends.
    @State private var handle: TextFieldModel

    init(state: LoginState) {
        self.state = state
        _handle = State(initialValue: TextFieldModel(state.handle))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("app_name")
                    .font(.largeTitle.bold())
                    .foregroundStyle(.tint)
                    .accessibilityAddTraits(.isHeader)
                Text("login_intro")
                notices
                AppTextField(
                    label: String(localized: "login_handle_label"),
                    model: handle,
                    state: state.handle,
                    options: TextFieldOptions(
                        keyboard: .URL, capitalization: .never, autocorrection: false, contentType: .username, submitLabel: .go
                    ),
                    onSubmit: submit
                )
                .disabled(state.busy)
                status
                if state.busy {
                    ProgressView()
                } else {
                    AppButton("login_sign_in", action: submit)
                        .disabled(!LoginText.canSubmit(handle.text))
                }
            }
            .font(.subheadline)
            .multilineTextAlignment(.center)
            .frame(maxWidth: 420)
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        // A short form sits in the middle of the screen, and scrolls only when large text or the keyboard leaves it no room.
        .defaultScrollAnchor(.center)
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.interactively)
        .background(Color(.systemGroupedBackground))
    }

    /// Why the athlete is here again, when it isn't a first sign-in.
    @ViewBuilder private var notices: some View {
        if let expired = state.expired {
            Text("login_session_expired")
            if expired.recording {
                Text("login_session_expired_recording")
            }
        }
        if let left = state.leftHandle {
            Text(LoginText.left(handle: left))
        }
        if state.signingInAgain {
            Text("login_sign_in_again")
        }
    }

    @ViewBuilder private var status: some View {
        switch state.status.sealedType() {
        case let .failed(failed):
            Text(LoginText.failure(failed.value))
                .foregroundStyle(.red)
        case .waitingForBrowser:
            Text("login_waiting_for_browser")
            Button("action_cancel") { state.eventSink(LoginEvent.Cancel.shared) }
        case .idle, .starting, .completing:
            EmptyView()
        }
    }

    private func submit() {
        state.eventSink(LoginEvent.Submit.shared)
    }
}

/// Sign-in's text that takes more than a name to build.
enum LoginText {
    /// Whether Sign in is offered: a handle of only spaces is no handle.
    static func canSubmit(_ handle: String) -> Bool {
        !handle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// The step of signing in that failed, and why when the reason is worth saying.
    static func failure(_ failed: AuthStatus.Failed) -> String {
        failure(stage: failed.stage, reason: failed.reason.flatMap { FailureReason.Companion.shared.of(error: $0) })
    }

    static func failure(stage: AuthStatus.Stage, reason: FailureReason?) -> String {
        let what =
            switch stage {
            case .Start: String(localized: "error_sign_in_start")
            case .Complete: String(localized: "error_sign_in_complete")
            case .FindAccount: String(localized: "error_sign_in_find_account")
            }
        return FailureText.text(what: what, reason: reason)
    }

    /// The message after leaving Activity Tracker, naming the account that is unchanged (auth/leaving).
    static func left(handle: String) -> String {
        String(format: String(localized: "login_left"), handle)
    }
}
