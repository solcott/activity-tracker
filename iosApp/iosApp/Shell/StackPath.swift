/// How a stack of the host's records, which Kotlin owns, meets SwiftUI's `NavigationStack(path:)`.
///
/// The stack's first record is the navigation stack's root view, so the path is the records above it. Screens open only from Kotlin (a
/// presenter navigates), so SwiftUI never lengthens the path: the one thing it writes is a shorter one, when the athlete goes back by a
/// swipe, the back button, or the back button's menu, which can close several screens at once.
enum StackPath {
    /// The path for a stack whose records have these keys, first record first.
    static func path(for keys: [String]) -> [String] {
        Array(keys.dropFirst())
    }

    /// What SwiftUI writing `newPath` asks of a stack with these keys: the number of records to shorten it to, or nil when the write
    /// changes nothing or isn't a shorter version of what is there.
    static func size(afterWriting newPath: [String], over keys: [String]) -> Int? {
        let current = path(for: keys)
        guard newPath.count < current.count, Array(current.prefix(newPath.count)) == newPath else { return nil }
        return newPath.count + 1
    }
}
