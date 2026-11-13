import SwiftUI

/// Editing the athlete's activity (feed/activity-visibility), a grouped form over the states `EditActivityPresenter` publishes: what they
/// wrote and its photos (`ActivityDetailsSections`), the gear (`GearFieldRow`), who sees it and each category (`VisibilityFormSections`)
/// and Leave out of leaderboards when offered, with a failed save at the top. Cancel leads the bar, since leaving asks first when
/// something changed (the stack's own back would leave without asking), and Save trails it. Saving asks first when it would narrow a
/// Public activity or widen its core charts, as the shared form does. What follows a rule is Kotlin's (`EditActivityTexts`).
struct EditActivityView: View {
    let state: EditActivityState

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadActivity, retry: { send(EditActivityEvent.Retry.shared) }) {
            if let details = state.details, let type = state.type {
                EditActivityForm(state: state, details: details, type: type)
            }
        }
        .pageTitle(Text(.editActivityTitle), canGoBack: false) {
            Button { send(EditActivityEvent.Save.shared) } label: { Text(LabelText.shared.save(saving: state.saving)) }
                .fontWeight(.semibold)
                .disabled(state.saving || state.details == nil)
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(.actionCancel) { send(EditActivityEvent.Back.shared) }
            }
        }
        .alert(
            Text(.editDiscardTitle),
            isPresented: Binding(get: { state.confirmDiscard }, set: { if !$0 { send(EditActivityEvent.KeepEditing.shared) } })
        ) {
            Button(.editDiscard, role: .destructive) { send(EditActivityEvent.Discard.shared) }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(.editDiscardMessage)
        }
        .alert(
            Text(.activityVisibilityNarrowingTitle),
            isPresented: Binding(get: { state.confirmNarrowing }, set: { if !$0 { send(EditActivityEvent.CancelNarrowing.shared) } })
        ) {
            Button(.activityVisibilityNarrowingConfirm) { send(EditActivityEvent.ConfirmNarrowing.shared) }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(.activityVisibilityNarrowingBody)
        }
        .alert(
            Text(.coreChartsWideningTitle),
            isPresented: Binding(
                get: { state.confirmCoreCharts != nil }, set: { if !$0 { send(EditActivityEvent.CancelCoreCharts.shared) } }),
            presenting: state.confirmCoreCharts
        ) { _ in
            Button(.coreChartsWideningConfirm) { send(EditActivityEvent.ConfirmCoreCharts.shared) }
            Button(.actionCancel, role: .cancel) {}
        } message: { visibility in
            Text(EditActivityTexts.shared.coreChartsBody(visibility: visibility))
        }
    }

    private func send(_ event: any EditActivityEvent) {
        state.eventSink(event)
    }
}

/// The loaded form, made once the activity's fields exist, so its text fields follow them from the start.
private struct EditActivityForm: View {
    let state: EditActivityState
    let details: ActivityDetailsFields
    let type: KotlinActivityType

    var body: some View {
        let enabled = !state.saving
        Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            ActivityDetailsSections(
                title: state.title, titleHint: LabelText.shared.activityType(type: type), fields: details, enabled: enabled)
            if let gear = state.gear {
                Section { GearFieldRow(field: gear, enabled: enabled) }
            }
            if let visibility = state.visibility {
                VisibilityFormSections(
                    fields: visibility, heading: Text(.activityVisibilityHeading),
                    activityEnabled: enabled && visibility.canChoose, categoriesEnabled: enabled
                ) {
                    Text(VisibilityTexts.shared.activityNote(fields: visibility))
                } detailsNote: {
                    EmptyView()
                }
            }
            if let excluded = state.leaderboardExclusion {
                Section {
                    Toggle(
                        isOn: Binding(
                            get: { excluded },
                            set: { state.eventSink(EditActivityEvent.LeaderboardExclusionChanged(excluded: $0)) })
                    ) {
                        Text(.editLeaderboardExclusion)
                    }
                    .disabled(!enabled)
                } footer: {
                    Text(.editLeaderboardExclusionHelp)
                }
            }
            if state.saving {
                Section { AppProgress().frame(maxWidth: .infinity) }
            }
        }
    }
}

#if DEBUG
    #Preview("Edit activity") {
        SamplePages(Samples.editActivity) { state in NavigationStack { EditActivityView(state: state) } }
    }

    #Preview("Edit activity, beyond loading") {
        SamplePages(Samples.editActivityVariants) { state in NavigationStack { EditActivityView(state: state) } }
    }
#endif
