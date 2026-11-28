import Foundation
import Testing

@testable import ActivityTracker

/// Counting a held repeating button's steps (`AppRepeatIconButton`, feed/activity-trim's nudges): SwiftUI repeats the action without
/// saying which step it is, so steps count up while one press lasts, and a release or a pause starts again.
struct RepeatCountTests {
    @Test func stepsOfOnePressCountUp() {
        var repeats = RepeatCount()
        let start = Date(timeIntervalSince1970: 0)
        #expect(repeats.next(at: start) == 0)
        #expect(repeats.next(at: start.addingTimeInterval(0.5)) == 1)
        #expect(repeats.next(at: start.addingTimeInterval(0.6)) == 2)
    }

    @Test func quickTapsEachStartAgain() {
        var repeats = RepeatCount()
        let start = Date(timeIntervalSince1970: 0)
        for tap in 0..<10 {
            #expect(repeats.next(at: start.addingTimeInterval(Double(tap) * 0.3)) == 0)
            repeats.released()
        }
    }

    @Test func aPauseStartsAgain() {
        var repeats = RepeatCount()
        let start = Date(timeIntervalSince1970: 0)
        _ = repeats.next(at: start)
        _ = repeats.next(at: start.addingTimeInterval(0.1))
        #expect(repeats.next(at: start.addingTimeInterval(2)) == 0)
    }
}
