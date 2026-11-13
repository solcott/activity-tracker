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
            // In a stack of its own, so the native page's title shows; a shared UI hides the stack's bar and draws its own.
            case .LeftElsewhere: NavigationStack { SingleScreen(record: session.leftElsewhere) }
            case .Open:
                // The athlete's units, for the native views that format stats (dev/shared-display-text).
                Following(session.shownUnits) { shown in
                    TabsView(session: session).environment(\.stats, Stats(units: shown.units))
                        // Route maps draw MapKit's live map in a session; previews and snapshots draw the line preview.
                        .environment(\.liveMaps, true)
                }
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
                    Tab(value: stack.destination) {
                        StackView(stack: stack)
                    } label: {
                        Label {
                            Text(stack.destination.title)
                        } icon: {
                            Image(stack.destination.icon)
                        }
                    }
                }
            }
        }
    }
}

/// One tab's screens, in a navigation stack whose path follows the Kotlin stack (see `StackPath`). The stack tells Kotlin how wide it is,
/// and Kotlin says when Settings shows beside its page (`shown.split`): the path then ends at Settings, whose destination draws the page
/// in a second column (ui/settings-layout).
private struct StackView: View {
    let stack: TabStack
    var body: some View {
        Following(stack.shown) { shown in
            let pushed = shown.pushed
            NavigationStack(path: path(of: pushed)) {
                // The first record is the stack's root. It changes only when the stack is replaced, and then the view is new too.
                ScreenView(record: pushed[0])
                    .environment(\.tabRoot, true)
                    .id(pushed[0].key)
                    .navigationDestination(for: StackEntry.self) { entry in
                        StackDestination(record: entry.record, stack: stack)
                    }
            }
            .onGeometryChange(for: CGSize.self) { $0.size } action: { stack.setSize(width: Double($0.width), height: Double($0.height)) }
        }
    }

    private func path(of records: [HostRecord]) -> Binding<[StackEntry]> {
        Binding(
            get: { records.dropFirst().map(StackEntry.init) },
            set: { newPath in
                // Shortening to a record also closes the pages drawn beside it, which Kotlin holds above it.
                if let size = StackPath.size(afterWriting: newPath.map(\.record.key), over: records.map(\.key)) {
                    stack.popTo(size: Int32(size))
                }
            }
        )
    }
}

/// A pushed record's screen. A record that can show a page beside it (Settings, which Kotlin marks with a `paneDefault`) is laid out
/// in columns, and follows the stack itself to know which page: a destination already pushed isn't rebuilt when the stack changes.
private struct StackDestination: View {
    let record: HostRecord
    let stack: TabStack

    var body: some View {
        if record.paneDefault == nil {
            ScreenView(record: record)
        } else {
            Following(stack.shown) { shown in
                // Only the Settings on top of the stack is beside a page; one further down is covered and draws as a list.
                let split = shown.split.flatMap { $0.settings.key == record.key ? $0 : nil }
                SettingsColumns(
                    list: {
                        ScreenView(record: record)
                            .environment(\.settingsLayout, split.map { SettingsLayout(pane: $0.pane) } ?? .single)
                    },
                    // A page replaces the one before it: each record's screen is its own view.
                    page: split.map { ScreenView(record: $0.page, inPane: true).id($0.page.key) })
            }
        }
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
    /// The tab's icon, as the shared design system names it; the tab bar fills the chosen one itself.
    var icon: AppIcon {
        switch self {
        case .Feed: .feed
        case .Search: .search
        case .You: .you
        }
    }
}
