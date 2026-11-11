import KotlinRuntimeSupport
import SwiftUI

/// A sub-screen (SubCircuit) that a record's state embeds, drawn natively from each state its presenter publishes: what
/// `SubCircuitContent(screen, outerEventSink)` is in the shared UI (dev/presenter-host). The host runs the sub-screen's presenter inside
/// the record's for as long as the record is in its stack, so it keeps its state while the record is covered, and comes back with it.
///
/// `screen` is the object the record's state holds (Swift has no type for `SubScreen`). `outerEvent` gets the sub-screen's outer events, as
/// the sub-screen's own types to cast, and hands them to the record's events as the shared UI does; the newest closure is the one used.
/// A sheet that stops drawing its sub-screen while the record still shows closes it (`HostSubRecord.close()`), so it opens afresh next time.
struct SubScreenView<State, Content: View>: View {
    private let sub: HostSubRecord
    private let content: (State) -> Content

    init(
        _ record: HostRecord,
        screen: any KotlinRuntimeSupport._KotlinBridgeable,
        outerEvent: @escaping (any SubCircuitOuterEvent) -> Void,
        @ViewBuilder content: @escaping (State) -> Content
    ) {
        sub = record.sub(screen: screen, outerEventSink: outerEvent)
        self.content = content
    }

    var body: some View {
        Following(sub.state) { state in
            // Nothing until the sub-screen's first state, which arrives within a frame of being asked for.
            if let state = state as? State {
                content(state)
            }
        }
        // A replaced sub-screen is another record: follow its flow, not the old one's.
        .id(sub.key)
    }
}
