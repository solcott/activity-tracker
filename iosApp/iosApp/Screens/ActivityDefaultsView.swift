import SwiftUI

/// Who new activities and each of their detail categories are shown to unless the athlete chooses otherwise (ui/settings-layout),
/// drawn from the states `ActivityDefaultsPresenter` publishes. A category offers only the activity's visibility or narrower. What it
/// says that follows a rule, and when it can be changed, is Kotlin's (`VisibilityTexts`, `SettingsTexts`, `canChange`), shared with
/// the Compose page.
///
/// Alone it is titled in the stack's bar; beside Settings' list, where it is the page shown until a row is chosen, in its own header.
struct ActivityDefaultsView: View {
    let state: ActivityDefaultsState

    var body: some View {
        Form {
            if let defaults = state.defaults {
                content(defaults)
            } else if let error = state.error {
                // Nothing to choose from until the defaults are read, as the shared page.
                Section { ErrorMessage(error, layout: .row) }
            }
        }
        .pageTitle(Text(.settingsActivityDefaults))
    }

    private func content(_ defaults: VisibilityFields) -> some View {
        let hasCategories = !defaults.categories.elements(as: CategoryVisibility.self).isEmpty
        return VisibilityFormSections(
            fields: defaults, heading: Text(.settingsDefaultsActivity), activityEnabled: state.canChange,
            categoriesEnabled: state.canChange
        ) {
            if !hasCategories { pageNote(defaults) }
        } detailsNote: {
            pageNote(defaults)
        }
    }

    /// What the page says last, under its last section, as the shared page ends with it: that a change is saving, why nothing can be
    /// chosen, or that each activity can differ; and a failed save.
    @ViewBuilder private func pageNote(_ defaults: VisibilityFields) -> some View {
        Text(SettingsTexts.shared.defaultsNote(defaults: defaults, saving: state.saving))
        if let error = state.error {
            ErrorMessage(error, layout: .row)
        }
    }
}

#if DEBUG
    #Preview("Activity defaults") {
        SamplePages(Samples.activityDefaults) { state in NavigationStack { ActivityDefaultsView(state: state) } }
    }

    #Preview("Activity defaults beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!)
                        .environment(\.settingsLayout, .beside(SettingsRows.activityDefaults))
                },
                page: ActivityDefaultsView(state: Samples.activityDefaults["loaded"]!).environment(\.inPane, true))
        }
    }
#endif
