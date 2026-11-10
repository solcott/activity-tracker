import Testing

@testable import ActivityTracker

/// How SwiftUI's navigation path meets a stack Kotlin owns (ui/ios-native-shell, dev/presenter-host).
struct StackPathTests {
    @Test func theFirstRecordIsTheRootAndTheRestAreThePath() {
        #expect(StackPath.path(for: ["feed"]) == [])
        #expect(StackPath.path(for: ["feed", "activity", "edit"]) == ["activity", "edit"])
    }

    @Test func goingBackOneScreenShortensTheStackByOne() {
        #expect(StackPath.size(afterWriting: ["activity"], over: ["feed", "activity", "edit"]) == 2)
    }

    @Test func goingBackSeveralScreensAtOnceShortensItToWhatIsLeft() {
        #expect(StackPath.size(afterWriting: [], over: ["feed", "activity", "edit"]) == 1)
    }

    @Test func writingThePathThatIsAlreadyThereChangesNothing() {
        #expect(StackPath.size(afterWriting: ["activity"], over: ["feed", "activity"]) == nil)
        #expect(StackPath.size(afterWriting: [], over: ["feed"]) == nil)
    }

    @Test func swiftUINeverOpensAScreen() {
        // A longer path, or one that isn't the start of what Kotlin holds, is ignored: screens open only from Kotlin.
        #expect(StackPath.size(afterWriting: ["activity", "edit"], over: ["feed", "activity"]) == nil)
        #expect(StackPath.size(afterWriting: ["other"], over: ["feed", "activity", "edit"]) == nil)
    }
}
