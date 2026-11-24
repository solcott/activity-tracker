import PhotosUI
import SwiftUI
import UIKit

/// What an athlete writes about an activity, as sections of a grouped form (the shared `ActivityDetailsForm`): the title, a dive's
/// site, the description, then the photos, each removable, with Add photos while the activity has room. Picked photos go through
/// the photo compression (`PhotoPick`) and are sent together, as the shared form sends them. What follows a rule is Kotlin's
/// (`DetailsFormTexts`). Its fields follow their Kotlin states, which exist once the form shows, so a page places it after loading.
struct ActivityDetailsSections: View {
    let title: KotlinTextFieldState
    /// What a blank title saves as, shown while it is blank: the activity's type.
    let titleHint: (any AppText)?
    let fields: ActivityDetailsFields
    var enabled = true

    @State private var titleModel: TextFieldModel
    @State private var diveSite: TextFieldModel?

    init(title: KotlinTextFieldState, titleHint: (any AppText)?, fields: ActivityDetailsFields, enabled: Bool = true) {
        self.title = title
        self.titleHint = titleHint
        self.fields = fields
        self.enabled = enabled
        _titleModel = State(initialValue: TextFieldModel(title))
        _diveSite = State(initialValue: fields.diveSite.map { TextFieldModel($0) })
    }

    var body: some View {
        Section {
            ActivityTitleField(label: .detailsTitleLabel, model: titleModel, state: title, hint: titleHint.map { Text($0) })
            if let diveSite, let site = fields.diveSite {
                AppTextField(label: .detailsDiveSiteLabel, model: diveSite, state: site, layout: .row)
            }
        }
        .disabled(!enabled)
        ActivityDescriptionSection(state: fields.about, enabled: enabled)
        ActivityPhotosSection(fields: fields, enabled: enabled)
    }
}

/// What the athlete writes about an activity, a multi-line field in a section of its own (the shared `ActivityDescriptionField`): the
/// details form's description, also on manual entry and Record. Its field follows its Kotlin state, with a counter as `ActivityTitleField`
/// has.
struct ActivityDescriptionSection: View {
    let state: KotlinTextFieldState
    var enabled = true

    @State private var model: TextFieldModel

    init(state: KotlinTextFieldState, enabled: Bool = true) {
        self.state = state
        self.enabled = enabled
        _model = State(initialValue: TextFieldModel(state))
    }

    var body: some View {
        Section {
            AppTextField(
                label: .detailsDescriptionLabel, model: model, state: state, layout: .multiline,
                hint: Text(.detailsDescriptionPlaceholder),
                supportingText: ActivityTextLimits.shared.descriptionCounter(text: model.text).map { AppStrings.string($0) },
                isError: !ActivityTextLimits.shared.descriptionFits(text: model.text))
        }
        .disabled(!enabled)
    }
}

/// An activity's title in a form's row, on every form that writes one (the shared `ActivityTitleField`), with its counter under it once
/// it nears its limit, red past it. The counter follows the text the field holds, ahead of the state, so it changes as each letter is
/// typed; Save follows the state's own rule (`textsFit`). No rule refuses typing, so a title already too long can still be shortened.
struct ActivityTitleField: View {
    let label: LocalizedStringResource
    let model: TextFieldModel
    let state: KotlinTextFieldState
    var hint: Text?

    var body: some View {
        AppTextField(
            label: label, model: model, state: state, layout: .row, hint: hint,
            supportingText: ActivityTextLimits.shared.titleCounter(text: model.text).map { AppStrings.string($0) },
            isError: !ActivityTextLimits.shared.titleFits(text: model.text))
    }
}

/// The form's photos: a tile each, the saved ones from their URLs and the picked ones from their bytes, with Remove on each, and a tile
/// to pick more from the library while the activity can hold them.
struct ActivityPhotosSection: View {
    let fields: ActivityDetailsFields
    var enabled = true
    @State private var picked: [PhotosPickerItem] = []
    @State private var preparing = false

    private static let tile: CGFloat = 88

    var body: some View {
        let media = fields.media.elements(as: (any EditMedia).self)
        Section {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: Self.tile), spacing: 8)], alignment: .leading, spacing: 8) {
                ForEach(media.indices, id: \.self) { index in
                    PhotoTile(media: media[index]) {
                        fields.eventSink(DetailsEvent.RemoveMedia(index: Int32(index)))
                    }
                    .disabled(!enabled)
                }
                if fields.canAddMedia {
                    PhotosPicker(
                        selection: $picked, maxSelectionCount: max(Int(fields.maxMedia) - media.count, 1), matching: .images,
                        preferredItemEncoding: .current
                    ) {
                        addTile
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled || preparing)
                }
            }
            .padding(.vertical, 4)
        } header: {
            HStack {
                Text(.detailsPhotosHeading)
                Spacer()
                Text(DetailsFormTexts.shared.photosCount(fields: fields))
            }
        } footer: {
            if preparing { Text(.detailsPreparingPhotos) }
        }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            preparing = true
            Task {
                let photos = await PhotoPick.app.pickAll(items)
                preparing = false
                if !photos.isEmpty { fields.eventSink(DetailsEvent.PhotosPicked(photos: photos)) }
            }
        }
    }

    private var addTile: some View {
        RoundedRectangle(cornerRadius: 8)
            .strokeBorder(Color(.separator))
            .frame(width: Self.tile, height: Self.tile)
            .overlay {
                if preparing {
                    AppProgress()
                } else {
                    VStack(spacing: 4) {
                        Image(systemName: "photo.badge.plus").font(.title2)
                        Text(.detailsAddPhotos).font(.caption)
                    }
                    .foregroundStyle(.tint)
                }
            }
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(.detailsAddPhotos))
            .accessibilityAddTraits(.isButton)
    }
}

/// One of the form's photos with its Remove button.
private struct PhotoTile: View {
    let media: any EditMedia
    let remove: () -> Void

    var body: some View {
        picture
            .frame(width: 88, height: 88)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(alignment: .topTrailing) {
                Button(action: remove) {
                    Image(systemName: "xmark.circle.fill")
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, .black.opacity(0.5))
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .padding(4)
                .accessibilityLabel(Text(.detailsRemovePhoto))
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(.detailsPhoto))
    }

    @ViewBuilder private var picture: some View {
        if let existing = media as? EditMedia.Existing, let photo = existing.item as? MediaPhoto {
            RemoteImage(photo.url, maxSize: CGSize(width: 88, height: 88)) { placeholder }
        } else if let picked = media as? EditMedia.Picked {
            PickedPhotoThumbnail(bytes: picked.photo.bytes, size: 88) { placeholder }
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        Rectangle().fill(Color(.tertiarySystemFill))
    }
}

/// The form's gear (gear/activity-gear): a row naming the items chosen, which opens a sheet of the athlete's items to tick. What it says
/// is Kotlin's (`GearFieldTexts`), shared with the Compose picker.
struct GearFieldRow: View {
    let field: GearField
    var enabled = true
    @State private var choosing = false

    var body: some View {
        AppListRow(Text(.gearFieldTitle), summary: Text(GearFieldTexts.shared.summary(field: field)), opens: { choosing = true })
            .disabled(!enabled)
            .sheet(isPresented: $choosing) {
                GearFieldSheet(field: field)
            }
    }
}

/// The athlete's items, each ticked when the activity lists it, with its kind and who may see it; Add collection and Remove all over
/// them. A tap ticks or unticks at once, as the shared sheet does, so Done only closes it.
struct GearFieldSheet: View {
    let field: GearField
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let options = field.options.elements(as: GearOption.self)
        NavigationStack {
            List {
                if options.isEmpty {
                    Text(.gearFieldEmpty).foregroundStyle(.secondary)
                } else {
                    Section {
                        ForEach(options, id: \.uri.value) { option in
                            row(option)
                        }
                    } footer: {
                        if let notAdded = GearFieldTexts.shared.notAdded(field: field) {
                            Text(notAdded).foregroundStyle(.red)
                        }
                    }
                }
            }
            .navigationTitle(Text(.gearFieldChoose))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(.gearFieldDone) { dismiss() } }
                if !options.isEmpty {
                    ToolbarItemGroup(placement: .bottomBar) {
                        collectionMenu
                        Spacer()
                        if field.offersClear {
                            Button(.gearFieldClear) { field.eventSink(GearFieldEvent.Clear.shared) }
                        }
                    }
                }
            }
        }
    }

    /// Add collection: each of the athlete's collections, adding its items. None when they have no collection.
    @ViewBuilder private var collectionMenu: some View {
        let collections = field.collections.elements(as: GearCollectionOption.self)
        if !collections.isEmpty {
            Menu {
                ForEach(collections, id: \.rkey) { collection in
                    Button(AppStrings.string(GearFieldTexts.shared.collection(collection: collection))) {
                        field.eventSink(GearFieldEvent.AddCollection(rkey: collection.rkey))
                    }
                }
            } label: {
                Text(.gearFieldAddCollection)
            }
        }
    }

    private func row(_ option: GearOption) -> some View {
        let chosen = field.isSelected(option: option)
        return AppCheckRow(checked: chosen, spacing: 12) {
            field.eventSink(GearFieldEvent.Toggle(uri: option.uri))
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: option.name).foregroundStyle(.primary)
                Text(GearFieldTexts.shared.line(option: option)).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}

/// The form's activity type (record/activity-type-picker): a row with the chosen type and its icon, which opens a searchable sheet of
/// every type, and under it the chosen sport's generic form and sub-sports to choose from (`SportTypeGroups.subSportChoices`).
struct ActivityTypeRow: View {
    let field: ActivityTypeField
    var enabled = true
    /// Leaves out types that can't use GPS, for recording, as the shared picker does.
    var outdoorOnly = false
    @State private var choosing = false

    var body: some View {
        let selected = field.selected
        let choices = SportTypeGroups.shared.subSportChoices(selected: selected, outdoorOnly: outdoorOnly).elements(
            as: KotlinActivityType.self)
        Button {
            choosing = true
        } label: {
            HStack(spacing: 12) {
                Text(.sportPickerLabel).foregroundStyle(.primary).frame(maxWidth: .infinity, alignment: .leading)
                SportIcon(selected).foregroundStyle(.tint)
                Text(LabelText.shared.activityType(type: selected)).foregroundStyle(.secondary).lineLimit(1).fixedSize()
                Image(systemName: "chevron.up.chevron.down").font(.footnote).foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityValue(Text(LabelText.shared.activityType(type: selected)))
        .sheet(isPresented: $choosing) {
            ActivityTypeSheet(field: field, outdoorOnly: outdoorOnly)
        }
        if !choices.isEmpty {
            FlowRow {
                ForEach(choices, id: \.key) { type in
                    let chosen = type.key == selected.key
                    AppChip(filled: chosen) {
                        field.eventSink(ActivityTypeEvent.Select(type: type))
                    } label: {
                        Text(LabelText.shared.activityType(type: type))
                    }
                    .accessibilityAddTraits(chosen ? .isSelected : [])
                }
            }
            .disabled(!enabled)
            .padding(.vertical, 4)
        }
    }
}

/// Every type to choose one from: a search field, the quick types while nothing is typed, then each category, a sport with sub-sports
/// under it collapsed until disclosed. While something is typed it lists every match (`SportTypeGroups.matching`, the shared rule),
/// expanded. A collapsed sport whose sub-sport is chosen names it under its label. Choosing closes the sheet.
struct ActivityTypeSheet: View {
    let field: ActivityTypeField
    /// Leaves out types that can't use GPS, quick types included, for recording.
    var outdoorOnly = false
    @State private var query: String
    @State private var expanded: Set<String>
    @Environment(\.dismiss) private var dismiss

    /// `query` starts the search typed and `expanded` the sports disclosed (by their token), for a preview or snapshot.
    init(field: ActivityTypeField, outdoorOnly: Bool = false, query: String = "", expanded: Set<String> = []) {
        self.field = field
        self.outdoorOnly = outdoorOnly
        _query = State(initialValue: query)
        _expanded = State(initialValue: expanded)
    }

    var body: some View {
        let searching = SportTypeSearch.shared.searching(query: query)
        let groups = SportTypeGroups.shared.matching(query: query, source: CatalogText.shared, outdoorOnly: outdoorOnly)
            .elements(as: SportTypeGroup.self)
        NavigationStack {
            List {
                if !searching {
                    Section {
                        ForEach(quickTypes, id: \.key) { choice($0) }
                    } header: {
                        Text(.sportPickerQuick)
                    }
                }
                if groups.isEmpty {
                    Text(.sportPickerNoMatches).foregroundStyle(.secondary)
                }
                SportTypeGroupsList(groups: groups, searching: searching, expanded: $expanded) { group in
                    Text(group.label)
                } row: { type, collapsed in
                    choice(type, hidden: hiddenChoice(in: collapsed))
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text(.sportPickerSearch))
            .navigationTitle(Text(.sportPickerLabel))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(.actionCancel) { dismiss() } }
            }
        }
    }

    private var quickTypes: [KotlinActivityType] {
        field.offeredQuickTypes(outdoorOnly: outdoorOnly)
    }

    /// The chosen type when it is one of `nested`, which a collapsed sport names under its label.
    private func hiddenChoice(in nested: [KotlinActivityType]) -> KotlinActivityType? {
        nested.first { $0.key == field.selected.key }
    }

    private func choice(_ type: KotlinActivityType, hidden: KotlinActivityType? = nil) -> some View {
        let chosen = type.key == field.selected.key
        return AppCheckRow(checked: chosen, spacing: 12) {
            field.eventSink(ActivityTypeEvent.Select(type: type))
            dismiss()
        } label: {
            SportIcon(type).foregroundStyle(.tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(LabelText.shared.activityType(type: type)).foregroundStyle(.primary)
                if let hidden {
                    Text(LabelText.shared.activityType(type: hidden)).font(.footnote).foregroundStyle(.tint)
                }
            }
        }
    }
}

#if DEBUG
    #Preview("Gear sheet") {
        if let state = Samples.manualActivityVariants["full"] {
            GearFieldSheet(field: state.gear)
        }
    }

    #Preview("Type sheet") {
        if let state = Samples.manualActivityVariants["full"] {
            ActivityTypeSheet(field: state.type)
        }
    }

    #Preview("Type sheet, searching") {
        if let state = Samples.manualActivityVariants["full"] {
            ActivityTypeSheet(field: state.type, query: "gravel")
        }
    }
#endif
