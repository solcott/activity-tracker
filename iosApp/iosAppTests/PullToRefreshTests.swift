import Testing

@testable import ActivityTracker

/// How long pull to refresh holds the system's spinner (ui/ios-design-system): until the page's refresh has run and ended, or briefly
/// when it never shows running. Counted in steps, with no real waiting, so a busy main actor can't change the answer.
@MainActor
struct PullToRefreshTests {
    private let step = Duration.milliseconds(100)

    @Test func itWaitsForTheRefreshToRunAndEnd() async {
        let flag = RefreshFlag()
        var sent = 0
        var looks = 0
        // Running from the second look to the fifth, then ended.
        let waited = await flag.run({ sent += 1 }, notStarted: .seconds(5), step: step) { _ in
            looks += 1
            flag.refreshing = (2...5).contains(looks)
        }
        #expect(sent == 1)
        #expect(waited == step * 6)
    }

    @Test func aRefreshThatNeverShowsRunningLetsTheSpinnerGo() async {
        let flag = RefreshFlag()
        let waited = await flag.run({}, notStarted: .milliseconds(800), step: step) { _ in }
        #expect(waited == .milliseconds(800))
    }

    @Test func noRefreshHoldsItLongerThanTheLimit() async {
        let flag = RefreshFlag()
        flag.refreshing = true
        let waited = await flag.run({}, atMost: .seconds(3), step: step) { _ in }
        #expect(waited == .seconds(3))
    }
}
