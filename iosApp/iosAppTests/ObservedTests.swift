import Testing

@testable import ActivityTracker

/// `Observed` over a plain Swift sequence. That a Kotlin flow is such a sequence, and that cancelling ends its collector, is what the
/// roadmap's phase 2 probe showed and the running app relies on.
@MainActor
struct ObservedTests {
    @Test func holdsItsInitialValueBeforeAnythingArrives() {
        #expect(Observed("first").value == "first")
    }

    @Test func endsOnTheLastElementOfTheSequence() async {
        let (stream, continuation) = AsyncStream<Int>.makeStream()
        let observed = Observed(0)
        let following = Task { await observed.follow(stream) }

        continuation.yield(1)
        continuation.yield(2)
        continuation.yield(3)
        continuation.finish()
        await following.value

        #expect(observed.value == 3)
    }

    @Test func stopsTakingElementsOnceItsTaskIsCancelled() async {
        let (stream, continuation) = AsyncStream<Int>.makeStream()
        let observed = Observed(0)
        let following = Task { await observed.follow(stream) }
        continuation.yield(1)
        while observed.value != 1 { await Task.yield() }

        following.cancel()
        await following.value
        continuation.yield(2)
        await Task.yield()

        #expect(observed.value == 1)
    }
}
