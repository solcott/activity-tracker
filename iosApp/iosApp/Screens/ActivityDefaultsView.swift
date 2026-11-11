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

    @ViewBuilder private func content(_ defaults: VisibilityFields) -> some View {
        let categories = defaults.categories.elements(as: CategoryVisibility.self)
        Section {
            picker(
                Text(.visibilityLabel),
                name: String(localized: .visibilityLabel),
                selection: Binding(
                    get: { defaults.activity }, set: { defaults.eventSink(VisibilityEvent.ActivityChanged(visibility: $0)) }),
                options: defaults.activityOptions.elements(as: String.self))
        } header: {
            Text(.settingsDefaultsActivity)
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                note(VisibilityTexts.shared.followersNote(selected: defaults.activity, approvesFollowers: defaults.approvesFollowers))
                if categories.isEmpty { pageNote(defaults) }
            }
        }
        if !categories.isEmpty {
            let options = defaults.categoryOptions.elements(as: String.self)
            Section {
                ForEach(categories, id: \.category) { category in
                    picker(
                        label(defaults, category.category),
                        name: AppStrings.string(VisibilityTexts.shared.categoryName(fields: defaults, category: category.category)),
                        selection: Binding(
                            get: { category.visibility },
                            set: { defaults.eventSink(VisibilityEvent.CategoryChanged(category: category.category, visibility: $0)) }),
                        options: options)
                }
            } header: {
                Text(.detailVisibilityHeading)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(.detailVisibilityHelp)
                    // The shared page says it under each such picker; in a grouped form it is said once for the section.
                    note(
                        categories.lazy.compactMap {
                            VisibilityTexts.shared.followersNote(selected: $0.visibility, approvesFollowers: defaults.approvesFollowers)
                        }.first)
                    pageNote(defaults)
                }
            }
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

    /// A category's name, with what it covers under it for the categories whose name doesn't say.
    private func label(_ defaults: VisibilityFields, _ category: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(VisibilityTexts.shared.categoryName(fields: defaults, category: category))
            if let help = VisibilityTexts.shared.categoryHelp(category: category) {
                Text(help).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    /// A menu of `options`, `Visibility` values each named as every picker names it, with the one chosen shown at the row's trailing
    /// edge (`AppPickerRow`). The presenter changes `selection` when told, and puts it back if the save fails. `name` is what VoiceOver
    /// calls it.
    private func picker(_ label: some View, name: String, selection: Binding<String>, options: [String]) -> some View {
        AppPickerRow(
            name: name, selection: selection, options: options,
            optionLabel: { AppStrings.string(LabelText.shared.visibility(visibility: $0)) }
        ) {
            label
        }
        .disabled(!state.canChange)
    }

    @ViewBuilder private func note(_ text: (any AppText)?) -> some View {
        if let text { Text(text) }
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
