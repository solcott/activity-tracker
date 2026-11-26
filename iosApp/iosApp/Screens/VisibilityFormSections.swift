import SwiftUI

/// Who an activity and each of its detail categories are shown to, as two sections of a grouped form (feed/activity-visibility,
/// feed/detail-visibility), drawn from Kotlin's `VisibilityFields`: the activity's visibility, then each category's, offering only the
/// activity's visibility or narrower. What they say that follows a rule is Kotlin's (`VisibilityTexts`). Activity defaults places them,
/// with its own heading and notes under the sections; an activity form places `ActivityPrivacySection` instead.
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
        let rows = VisibilityRows(fields: fields)
        Section {
            rows.activityPicker(Text(.visibilityLabel), name: String(localized: .visibilityLabel)).disabled(!activityEnabled)
        } header: {
            heading
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                rows.note(VisibilityTexts.shared.followersNote(selected: fields.activity, approvesFollowers: fields.approvesFollowers))
                activityNote()
            }
        }
        if !rows.categories.isEmpty {
            Section {
                rows.categoryPickers.disabled(!categoriesEnabled)
            } header: {
                Text(.detailVisibilityHeading)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(.detailVisibilityHelp)
                    // The shared form says it under each such picker; in a grouped form it is said once for the section.
                    rows.note(rows.categoriesFollowersNote)
                    detailsNote()
                }
            }
        }
    }
}

/// An activity form's Privacy section (import, edit, manual entry, recording, accepting an invite): who may see the activity, its detail
/// categories folded under a row summing them up (`DetailsSummary.of`) until opened, then the rows the form adds (`more`,
/// such as Edit's leaderboard choice), with the notes under the section.
struct ActivityPrivacySection<More: View>: View {
    let fields: VisibilityFields
    /// What the activity's picker is called: who can see this activity, or the batch's.
    var label = String(localized: .activityVisibilityHeading)
    let enabled: Bool
    @ViewBuilder let more: () -> More
    @State private var detailsExpanded = false

    var body: some View {
        let rows = VisibilityRows(fields: fields)
        Section {
            rows.activityPicker(Text(verbatim: label), name: label).disabled(!(enabled && fields.canChoose))
            if !rows.categories.isEmpty {
                DisclosureGroup(isExpanded: $detailsExpanded) {
                    if fields.offersMatchActivity || fields.offersDefaults {
                        // AppTextButton is borderless, so each takes only its own taps in the shared row.
                        HStack(spacing: 16) {
                            if fields.offersMatchActivity {
                                AppTextButton(.detailVisibilityMatchActivity) { fields.eventSink(VisibilityEvent.MatchActivity.shared) }
                            }
                            if fields.offersDefaults {
                                AppTextButton(.detailVisibilityUseDefaults) { fields.eventSink(VisibilityEvent.UseDefaults.shared) }
                            }
                        }
                        .disabled(!enabled)
                    }
                    rows.categoryPickers.disabled(!enabled)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(.detailVisibilityRow)
                        ForEach(0..<DetailsSummary.shared.of(fields: fields).count, id: \.self) { index in
                            DetailsSummaryText(line: DetailsSummary.shared.line(fields: fields, index: Int32(index)))
                        }
                    }
                }
            }
            more()
        } header: {
            Text(.activityPrivacyHeading)
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                // Said once, whether the activity or (shown) a detail is for followers.
                rows.note(
                    VisibilityTexts.shared.followersNote(selected: fields.activity, approvesFollowers: fields.approvesFollowers)
                        ?? (detailsExpanded ? rows.categoriesFollowersNote : nil))
                if detailsExpanded {
                    Text(.detailVisibilityHelp)
                }
                Text(VisibilityTexts.shared.activityNote(fields: fields))
            }
        }
        // Set off from the form's other sections, as the shared form does with its divider and heading.
        .headerProminence(.increased)
    }
}

extension ActivityPrivacySection where More == EmptyView {
    init(fields: VisibilityFields, label: String = String(localized: .activityVisibilityHeading), enabled: Bool) {
        self.init(fields: fields, label: label, enabled: enabled) { EmptyView() }
    }
}

/// A summary line, its tier set off in semibold, all of it secondary so the row's title stays the strongest text.
private struct DetailsSummaryText: View {
    let line: DetailsSummaryLine

    var body: some View {
        var text = AttributedString(AppStrings.string(line.text))
        if let tier = text.range(of: AppStrings.string(line.tier)) {
            text[tier].inlinePresentationIntent = .stronglyEmphasized
        }
        return Text(text).font(.subheadline).foregroundStyle(.secondary)
    }
}

/// The rows both layouts draw from the same fields.
private struct VisibilityRows {
    let fields: VisibilityFields

    var categories: [CategoryVisibility] { fields.categories.elements(as: CategoryVisibility.self) }

    /// The activity's picker, called `label` (`name` for VoiceOver).
    func activityPicker(_ label: Text, name: String) -> some View {
        picker(
            label,
            name: name,
            selection: Binding(get: { fields.activity }, set: { fields.eventSink(VisibilityEvent.ActivityChanged(visibility: $0)) }),
            options: fields.activityOptions.elements(as: String.self)
        )
    }

    /// A picker per category, each offering the activity's visibility or narrower.
    var categoryPickers: some View {
        let options = fields.categoryOptions.elements(as: String.self)
        return ForEach(categories, id: \.category) { category in
            picker(
                label(category.category),
                name: AppStrings.string(VisibilityTexts.shared.categoryName(fields: fields, category: category.category)),
                selection: Binding(
                    get: { category.visibility },
                    set: { fields.eventSink(VisibilityEvent.CategoryChanged(category: category.category, visibility: $0)) }),
                options: options
            )
        }
    }

    /// The followers note, if any category is shown to followers.
    var categoriesFollowersNote: (any AppText)? {
        categories.lazy.compactMap {
            VisibilityTexts.shared.followersNote(selected: $0.visibility, approvesFollowers: fields.approvesFollowers)
        }.first
    }

    @ViewBuilder func note(_ text: (any AppText)?) -> some View {
        if let text { Text(text) }
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
}
