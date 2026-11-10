import SwiftUI

/// Segment matching with its leaderboard choices, then activity grouping, each with what it shares and why (ui/settings-layout),
/// drawn from the states `SegmentsAndGroupsPresenter` publishes. When each choice is offered and can be changed, which grouping
/// choice shows as chosen, the choices themselves and the notes under them are Kotlin's, shared with the Compose page.
struct SegmentsAndGroupsView: View {
    let state: SegmentsAndGroupsState

    var body: some View {
        Form {
            Section {
                Toggle(isOn: binding(state.segmentMatching == true) { SegmentsAndGroupsEvent.SegmentMatchingChanged(on: $0) }) {
                    Text(.settingsSegmentsMatching)
                }
                .disabled(!state.canChangeMatching)
                if state.offersLeaderboards {
                    Toggle(isOn: binding(state.showOnLeaderboards == true) { SegmentsAndGroupsEvent.LeaderboardsChanged(shown: $0) }) {
                        Text(.settingsSegmentsLeaderboards)
                    }
                    .disabled(!state.canChangeLeaderboards)
                }
                if state.offersBracketBoards {
                    Toggle(isOn: binding(state.bracketBoards == true) { SegmentsAndGroupsEvent.BracketBoardsChanged(on: $0) }) {
                        Text(.settingsSegmentsBrackets)
                        Text(.settingsSegmentsBracketsHelp)
                    }
                    .disabled(!state.canChangeBracketBoards)
                }
            } header: {
                Text(.settingsSegmentsHeading)
            } footer: {
                Text(SettingsTexts.shared.segmentsNote(state: state))
            }
            Section {
                groups
            } header: {
                Text(.settingsGroupsHeading)
            } footer: {
                Text(SettingsTexts.shared.groupsNote(state: state))
            }
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
        }
        .pageTitle(Text(.settingsSegmentsAndGroups))
    }

    /// Off, people the athlete follows or who follow them, or anyone: the options Kotlin offers, each holding its choice.
    private var groups: some View {
        Picker(selection: Binding(get: { state.shownGroupChoice }, set: { state.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(choice: $0)) })) {
            ForEach(state.groupOptions.elements(as: GroupOption.self), id: \.choice) { option in
                Text(option.label).tag(option.choice)
            }
        } label: {
            Text(.settingsGroupsHeading)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .disabled(!state.canChangeGroups)
    }

    /// A switch's value, which the presenter changes when told, and puts back if the save fails.
    private func binding(_ value: Bool, event: @escaping (Bool) -> any SegmentsAndGroupsEvent) -> Binding<Bool> {
        Binding(get: { value }, set: { state.eventSink(event($0)) })
    }
}

#if DEBUG
    #Preview("Segments & groups") {
        SamplePages(Samples.segmentsAndGroups) { state in NavigationStack { SegmentsAndGroupsView(state: state) } }
    }

    #Preview("Segments & groups beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!)
                        .environment(\.settingsLayout, .beside(SettingsRows.segmentsAndGroups))
                },
                page: SegmentsAndGroupsView(state: Samples.segmentsAndGroups["loaded"]!).environment(\.inPane, true))
        }
    }
#endif
