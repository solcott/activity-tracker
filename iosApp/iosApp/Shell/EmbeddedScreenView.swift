import KotlinRuntimeSupport
import SwiftUI

/// A screen a record's page shows inside itself, drawn natively from each state its presenter publishes: what `CircuitContent(screen)`
/// inside a screen's UI is in the shared UI (dev/presenter-host; the Search tab's segment browse). The host runs it inside the record's
/// presenter for as long as the record is in its stack, so it keeps its state while the record is covered, and what it opens goes on the
/// record's stack.
///
/// `screen` is the screen object (Swift has no type for Circuit's `Screen`). Sheets inside it ask the embedded record for their sub-screens.
struct EmbeddedScreenView<State, Content: View>: View {
    private let inner: HostRecord
    private let content: (State) -> Content

    init(_ record: HostRecord, screen: any KotlinRuntimeSupport._KotlinBridgeable, @ViewBuilder content: @escaping (State) -> Content) {
        inner = record.embed(screen: screen)
        self.content = content
    }

    var body: some View {
        Following(inner.state) { state in
            // Nothing until the screen's first state, which arrives within a frame of being asked for.
            if let state = state as? State {
                content(state).environment(\.hostRecord, inner)
            }
        }
        .id(inner.key)
    }
}
