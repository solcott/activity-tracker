import Foundation
import Testing

@testable import ActivityTracker

/// Counting a held nudge button's steps (feed/activity-trim): SwiftUI repeats the action without saying so, so steps close together in one
/// direction are one hold, and a pause or the other direction starts again.
struct NudgeRepeatTests {
    @Test func stepsCloseTogetherCountAsOneHold() {
        var repeats = NudgeRepeat()
        let start = Date(timeIntervalSince1970: 0)
        #expect(repeats.next(later: true, at: start) == 0)
        #expect(repeats.next(later: true, at: start.addingTimeInterval(0.5)) == 1)
        #expect(repeats.next(later: true, at: start.addingTimeInterval(0.6)) == 2)
    }

    @Test func aPauseOrTheOtherDirectionStartsAgain() {
        var repeats = NudgeRepeat()
        let start = Date(timeIntervalSince1970: 0)
        _ = repeats.next(later: true, at: start)
        _ = repeats.next(later: true, at: start.addingTimeInterval(0.1))
        #expect(repeats.next(later: false, at: start.addingTimeInterval(0.2)) == 0)
        #expect(repeats.next(later: false, at: start.addingTimeInterval(2)) == 0)
    }
}
