import SwiftUI

/// The choice after leaving Activity Tracker on another device (auth/leaving), shown in place of the app while this device still holds
/// work for the account, drawn from the states `LeftElsewherePresenter` publishes: what this device holds, Rejoin to upload it, or Discard
/// and sign out; both off while either runs. Progress while it is still reading what the device holds. What it says that follows a rule
/// is Kotlin's (`LeftElsewhereTexts`), shared with the Compose page.
struct LeftElsewhereView: View {
    let state: LeftElsewhereState

    var body: some View {
        Group {
            if state.checking {
                AppProgress().frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(.systemGroupedBackground))
            } else {
                List {
                    Section {
                        Text(LeftElsewhereTexts.shared.intro(state: state))
                        ForEach(Array(LeftElsewhereTexts.shared.lines(state: state).enumerated()), id: \.offset) { _, line in
                            Text(line)
                        }
                    } footer: {
                        Text(.leftElsewhereChoice)
                    }
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            if let error = state.error {
                                ErrorMessage(error, layout: .row)
                            }
                            AppButton(.leftElsewhereRejoin) { state.eventSink(LeftElsewhereEvent.Rejoin.shared) }
                            AppSecondaryButton(.leftElsewhereDiscard) { state.eventSink(LeftElsewhereEvent.Discard.shared) }
                            if state.busy {
                                AppProgress(small: true)
                            }
                        }
                        .disabled(state.busy)
                        .padding(.vertical, 4)
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        // Shown in place of the app, so there is nowhere to go back to.
        .pageTitle(Text(.leftElsewhereTitle), canGoBack: false)
    }
}

#if DEBUG
    #Preview("Left on another device") {
        SamplePages(Samples.leftElsewhere) { state in NavigationStack { LeftElsewhereView(state: state) } }
    }
#endif
