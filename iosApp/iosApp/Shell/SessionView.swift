import SwiftUI

/// A signed-in account: the tabs, or the one screen a leave shows in place of them (auth/leaving).
struct SessionView: View {
    let session: SessionHost

    var body: some View {
        // The flows followed here carry objects (`shownMode`, `shownTab`, `shown`): a flow's elements reach Swift only as objects, so a
        // flow of a Kotlin enum or list has a twin that wraps each value.
        Following(session.shownMode) { shown in
            switch shown.mode {
            // Not yet known whether a leave is recorded on this device: nothing, for the moment the database takes to answer.
            case .Unknown: Color(.systemGroupedBackground).ignoresSafeArea()
            case .Leaving: SingleScreen(record: session.leaving)
            case .LeftElsewhere: SingleScreen(record: session.leftElsewhere)
            case .Open: TabsView(session: session)
            }
        }
    }
}

/// The signed-in app: a navigation stack per tab. Kotlin owns which tab is chosen and what each stack holds; this shows them and reports
/// what the athlete did.
private struct TabsView: View {
    let session: SessionHost

    var body: some View {
        Following(session.shownTab) { shown in
            // Choosing the tab already on screen reaches `select` too, which returns that tab to its first screen.
            TabView(selection: Binding(get: { shown.destination }, set: { session.select(destination: $0) })) {
                ForEach(session.tabs, id: \.destination) { stack in
                    Tab(stack.destination.title, systemImage: stack.destination.symbol, value: stack.destination) {
                        StackView(stack: stack)
                    }
                }
            }
        }
    }
}

/// One tab's screens, in a navigation stack whose path follows the Kotlin stack (see `StackPath`).
private struct StackView: View {
    let stack: TabStack
    @State private var controllers = FallbackControllers()

    var body: some View {
        Following(stack.shown) { shown in
            let records = shown.records
            let keys = records.map(\.key)
            NavigationStack(path: path(of: records)) {
                // The first record is the stack's root. It changes only when the stack is replaced, and then the view is new too.
                ScreenView(record: records[0], controllers: controllers)
                    .id(records[0].key)
                    .navigationDestination(for: StackEntry.self) { entry in
                        ScreenView(record: entry.record, controllers: controllers)
                    }
            }
            .onChange(of: keys) { _, keys in controllers.keep(only: keys) }
        }
    }

    private func path(of records: [HostRecord]) -> Binding<[StackEntry]> {
        Binding(
            get: { records.dropFirst().map(StackEntry.init) },
            set: { newPath in
                if let size = StackPath.size(afterWriting: newPath.map(\.record.key), over: records.map(\.key)) {
                    stack.popTo(size: Int32(size))
                }
            }
        )
    }
}

/// A record as an element of a navigation path. It carries the record, so a screen sliding away after Kotlin removed it from the stack
/// still has what it draws.
private struct StackEntry: Hashable {
    let record: HostRecord

    static func == (lhs: StackEntry, rhs: StackEntry) -> Bool {
        lhs.record.key == rhs.record.key
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(record.key)
    }
}

extension TopLevelDestination {
    /// The tab's name: the shared UI's string of the same name (`Localizable.xcstrings`).
    var title: LocalizedStringKey {
        switch self {
        case .Feed: "nav_feed"
        case .Search: "nav_search"
        case .You: "nav_you"
        }
    }

    /// The SF Symbol the shared UI's `AppIcon` names for the tab; the tab bar fills the chosen one itself.
    var symbol: String {
        switch self {
        case .Feed: "house"
        case .Search: "magnifyingglass"
        case .You: "person"
        }
    }
}
