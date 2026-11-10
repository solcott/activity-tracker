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
                Text(.appName)
                    .font(.largeTitle.bold())
                    .foregroundStyle(.tint)
                    .accessibilityAddTraits(.isHeader)
                Text(.loginIntro)
                notices
                AppTextField(
                    label: .loginHandleLabel,
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
                    AppButton(.loginSignIn, action: submit)
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
            Text(.loginSessionExpired)
            if expired.recording {
                Text(.loginSessionExpiredRecording)
            }
        }
        if let left = state.leftHandle {
            Text(LoginTexts.shared.left(handle: left))
        }
        if state.signingInAgain {
            Text(.loginSignInAgain)
        }
    }

    @ViewBuilder private var status: some View {
        switch state.status.sealedType() {
        case let .failed(failed):
            ErrorMessage(text: AppStrings.string(LoginTexts.shared.failure(status: failed.value)))
        case .waitingForBrowser:
            Text(.loginWaitingForBrowser)
            AppTextButton(.actionCancel) { state.eventSink(LoginEvent.Cancel.shared) }
        case .idle, .starting, .completing:
            EmptyView()
        }
    }

    private func submit() {
        state.eventSink(LoginEvent.Submit.shared)
    }
}

/// Sign-in's choices that aren't layout. What its failures and notices say is Kotlin's `LoginTexts`, shared with the Compose screen.
enum LoginText {
    /// Whether Sign in is offered: a handle of only spaces is no handle.
    static func canSubmit(_ handle: String) -> Bool {
        !handle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
