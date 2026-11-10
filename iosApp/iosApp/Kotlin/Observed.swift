import Observation
import SwiftUI

/// The latest element of an asynchronous sequence, held where a view can read it: how the shell follows a Kotlin flow, such as the
/// states a screen's presenter publishes. Kotlin's flows arrive as `AsyncSequence`s (`asAsyncSequence()`), and their elements arrive on
/// the main actor here because `follow` runs on it.
@Observable
final class Observed<Value> {
    private(set) var value: Value

    init(_ initial: Value) {
        value = initial
    }

    /// Takes each element of `sequence` as the value, until the sequence ends or the task this runs in is cancelled. Cancelling the task
    /// also ends the Kotlin collector behind a flow's sequence.
    func follow<Elements: AsyncSequence>(_ sequence: Elements) async where Elements.Element == Value {
        do {
            for try await element in sequence {
                value = element
            }
        } catch {
            // Cancelled, or the flow ended with an error; either way there is nothing more to take.
        }
    }
}
