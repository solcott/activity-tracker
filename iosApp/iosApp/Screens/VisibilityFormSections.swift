import SwiftUI

/// Who an activity and each of its detail categories are shown to, as two sections of a grouped form (feed/activity-visibility,
/// feed/detail-visibility), drawn from Kotlin's `VisibilityFields`: the activity's visibility, then each category's, offering only the
/// activity's visibility or narrower. What they say that follows a rule is Kotlin's (`VisibilityTexts`). Activity defaults and Accept
/// invite place them, each with its own heading and notes under the sections.
struct VisibilityFormSections<ActivityNote: View, DetailsNote: View>: View {
    let fields: VisibilityFields
    let heading: Text
    var activityEnabled = true
    var categoriesEnabled = true
    /// What the page adds under the activity's section, after its followers note.
    @ViewBuilder let activityNote: () -> ActivityNote
    /// What the page adds under the categories' section, last.
    @ViewBuilder let detailsNote: () -> DetailsNote

    var body: some View {
        let categories = fields.categories.elements(as: CategoryVisibility.self)
        Section {
            picker(
                Text(.visibilityLabel),
                name: String(localized: .visibilityLabel),
                selection: Binding(get: { fields.activity }, set: { fields.eventSink(VisibilityEvent.ActivityChanged(visibility: $0)) }),
                options: fields.activityOptions.elements(as: String.self)
            )
            .disabled(!activityEnabled)
        } header: {
            heading
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                note(VisibilityTexts.shared.followersNote(selected: fields.activity, approvesFollowers: fields.approvesFollowers))
                activityNote()
            }
        }
        if !categories.isEmpty {
            let options = fields.categoryOptions.elements(as: String.self)
            Section {
                ForEach(categories, id: \.category) { category in
                    picker(
                        label(category.category),
                        name: AppStrings.string(VisibilityTexts.shared.categoryName(fields: fields, category: category.category)),
                        selection: Binding(
                            get: { category.visibility },
                            set: { fields.eventSink(VisibilityEvent.CategoryChanged(category: category.category, visibility: $0)) }),
                        options: options
                    )
                    .disabled(!categoriesEnabled)
                }
            } header: {
                Text(.detailVisibilityHeading)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(.detailVisibilityHelp)
                    // The shared form says it under each such picker; in a grouped form it is said once for the section.
                    note(
                        categories.lazy.compactMap {
                            VisibilityTexts.shared.followersNote(selected: $0.visibility, approvesFollowers: fields.approvesFollowers)
                        }.first)
                    detailsNote()
                }
            }
        }
    }

    /// A category's name, with what it covers under it for the categories whose name doesn't say.
    private func label(_ category: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(VisibilityTexts.shared.categoryName(fields: fields, category: category))
            if let help = VisibilityTexts.shared.categoryHelp(category: category) {
                Text(help).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    /// A menu of `options`, `Visibility` values each named as every picker names it, with the one chosen shown at the row's trailing
    /// edge (`AppPickerRow`). The presenter changes `selection` when told. `name` is what VoiceOver calls it.
    private func picker(_ label: some View, name: String, selection: Binding<String>, options: [String]) -> some View {
        AppPickerRow(
            name: name, selection: selection, options: options,
            optionLabel: { AppStrings.string(LabelText.shared.visibility(visibility: $0)) }
        ) {
            label
        }
    }

    @ViewBuilder private func note(_ text: (any AppText)?) -> some View {
        if let text { Text(text) }
    }
}
