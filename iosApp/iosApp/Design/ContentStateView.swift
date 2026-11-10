import AppCommonApi
import SwiftUI

/// Content a screen loads, laid out as ui/content-loading says: progress until the first answer, what failed with Retry when nothing
/// has loaded, and from then on the content, kept through every refresh. Which of the three is Kotlin's `ContentShown`, the rule the
/// shared UI follows too.
///
/// `content` draws the loaded value and places `RefreshStatusView` where a failed or running refresh should show over it. The value
/// reaches Swift untyped, since Swift export erases the state's type parameter, so the view names the type it expects.
struct ContentStateView<Value, Content: View>: View {
    private let state: KotlinContentState
    private let errorKind: UiError.Kind
    private let retry: () -> Void
    private let content: (Value) -> Content

    init(
        _ state: KotlinContentState, as value: Value.Type = Value.self, errorKind: UiError.Kind, retry: @escaping () -> Void,
        @ViewBuilder content: @escaping (Value) -> Content
    ) {
        self.state = state
        self.errorKind = errorKind
        self.retry = retry
        self.content = content
    }

    var body: some View {
        switch ContentShown.Companion.shared.of(state: state, errorKind: errorKind).sealedType() {
        case .progress:
            AppProgress().frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .failure(failure):
            ErrorMessage(failure.value.error, retry: retry)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        case .content:
            if let value = state.data as? Value {
                content(value)
            }
        }
    }
}

/// Over loaded content: why the last refresh failed, with Retry, or a small spinner while one runs (unless `showsProgress` is off,
/// for a pull to refresh that shows its own). Nothing once it settles, or before anything has loaded.
struct RefreshStatusView: View {
    let state: KotlinContentState
    let errorKind: UiError.Kind
    var showsProgress = true
    let retry: () -> Void

    var body: some View {
        if case let .content(content) = ContentShown.Companion.shared.of(state: state, errorKind: errorKind).sealedType() {
            if let error = content.value.refreshError {
                ErrorMessage(error, layout: .row, retry: retry)
            } else if content.value.refreshing, showsProgress {
                AppProgress(small: true).frame(maxWidth: .infinity)
            }
        }
    }
}
