import KotlinCoroutineSupport
import SwiftUI

/// Draws `content` with the latest value of a Kotlin state flow, and follows the flow for as long as the view is on screen: when the
/// view goes away the flow's collector is cancelled, and when it comes back it starts from the flow's current value.
///
/// The view keeps the first flow it is given. Where the flow can change (another record, another session), give the view an `id` that
/// changes with it.
struct Following<Value, Content: View>: View {
    private let flow: any KotlinTypedStateFlow<Value>
    private let content: (Value) -> Content
    @State private var observed: Observed<Value>

    init(_ flow: any KotlinTypedStateFlow<Value>, @ViewBuilder content: @escaping (Value) -> Content) {
        self.flow = flow
        self.content = content
        _observed = State(initialValue: Observed(flow.value))
    }

    var body: some View {
        // The stack is what follows the flow, not the content: content that draws nothing for the first value, as a screen does until
        // its presenter's first state, is no view at all, and a task on no view never starts. The screen would stay empty for good.
        ZStack { content(observed.value) }
            .task { await observed.follow(flow.asAsyncSequence()) }
    }
}
