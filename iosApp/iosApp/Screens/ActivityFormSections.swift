import PhotosUI
import SwiftUI
import UIKit

/// What an athlete writes about an activity, as sections of a grouped form (the shared `ActivityDetailsForm`): the title, a dive's
/// site, the description, then the photos, each removable, with Add photos while the activity has room. Picked photos go through
/// Kotlin's compression (`PhotoPick`) and are sent together, as the shared form sends them. What follows a rule is Kotlin's
/// (`DetailsFormTexts`). Its fields follow their Kotlin states, which exist once the form shows, so a page places it after loading.
struct ActivityDetailsSections: View {
    let title: KotlinTextFieldState
    /// What a blank title saves as, shown while it is blank: the activity's type.
    let titleHint: (any AppText)?
    let fields: ActivityDetailsFields
    var enabled = true

    @State private var titleModel: TextFieldModel
    @State private var description: TextFieldModel
    @State private var diveSite: TextFieldModel?

    init(title: KotlinTextFieldState, titleHint: (any AppText)?, fields: ActivityDetailsFields, enabled: Bool = true) {
        self.title = title
        self.titleHint = titleHint
        self.fields = fields
        self.enabled = enabled
        _titleModel = State(initialValue: TextFieldModel(title))
        _description = State(initialValue: TextFieldModel(fields.about))
        _diveSite = State(initialValue: fields.diveSite.map { TextFieldModel($0) })
    }

    var body: some View {
        Section {
            AppTextField(
                label: .detailsTitleLabel, model: titleModel, state: title, layout: .row, hint: titleHint.map { Text($0) })
            if let diveSite, let site = fields.diveSite {
                AppTextField(label: .detailsDiveSiteLabel, model: diveSite, state: site, layout: .row)
            }
        }
        .disabled(!enabled)
        Section {
            AppTextField(
                label: .detailsDescriptionLabel, model: description, state: fields.about, layout: .multiline,
                hint: Text(.detailsDescriptionPlaceholder))
        }
        .disabled(!enabled)
        ActivityPhotosSection(fields: fields, enabled: enabled)
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
                        preferredItemEncoding: .compatible
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
                var photos: [PickedPhoto] = []
                for item in items {
                    await PhotoPick.app.pick({ try await item.loadTransferable(type: Data.self) }) { photos.append($0) }
                }
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
                    ProgressView()
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
            RemoteImage(photo.url) { placeholder }
        } else if let picked = media as? EditMedia.Picked, let image = UIImage(data: picked.photo.bytes.data) {
            Image(uiImage: image).resizable().scaledToFill()
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
        return Button { field.eventSink(GearFieldEvent.Toggle(uri: option.uri)) } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: option.name).foregroundStyle(.primary)
                    Text(GearFieldTexts.shared.line(option: option)).font(.footnote).foregroundStyle(.secondary)
                }
                Spacer()
                if chosen {
                    Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(chosen ? .isSelected : [])
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
        let choices = SportTypeGroups.shared.subSportChoices(selected: selected, outdoorOnly: outdoorOnly).elements(as: KotlinActivityType.self)
        Button { choosing = true } label: {
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
                    Button { field.eventSink(ActivityTypeEvent.Select(type: type)) } label: {
                        Text(LabelText.shared.activityType(type: type))
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(chosen ? AnyShapeStyle(Color(.onAccent)) : AnyShapeStyle(.tint))
                    }
                    .typeChip(chosen: chosen)
                    .accessibilityAddTraits(chosen ? .isSelected : [])
                }
            }
            .disabled(!enabled)
            .padding(.vertical, 4)
        }
    }
}

extension View {
    @ViewBuilder fileprivate func typeChip(chosen: Bool) -> some View {
        if chosen {
            buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
        } else {
            buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
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
                ForEach(groups, id: \.category) { group in
                    Section {
                        ForEach(group.rows.elements(as: SportTypeRow.self), id: \.type.key) { sport in
                            let nested = sport.nested.elements(as: KotlinActivityType.self)
                            if nested.isEmpty {
                                choice(sport.type)
                            } else {
                                // A search shows every match, so none hides behind a collapsed sport.
                                DisclosureGroup(isExpanded: searching ? .constant(true) : isExpanded(sport.type.sport)) {
                                    ForEach(nested, id: \.key) { choice($0) }
                                } label: {
                                    choice(sport.type, hidden: searching || expanded.contains(sport.type.sport) ? nil : hiddenChoice(in: nested))
                                }
                            }
                        }
                    } header: {
                        Text(group.label)
                    }
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

    private func isExpanded(_ sport: String) -> Binding<Bool> {
        Binding(
            get: { expanded.contains(sport) },
            set: { open in if open { expanded.insert(sport) } else { expanded.remove(sport) } })
    }

    private func choice(_ type: KotlinActivityType, hidden: KotlinActivityType? = nil) -> some View {
        let chosen = type.key == field.selected.key
        return Button {
            field.eventSink(ActivityTypeEvent.Select(type: type))
            dismiss()
        } label: {
            HStack(spacing: 12) {
                SportIcon(type).foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(LabelText.shared.activityType(type: type)).foregroundStyle(.primary)
                    if let hidden {
                        Text(LabelText.shared.activityType(type: hidden)).font(.footnote).foregroundStyle(.tint)
                    }
                }
                Spacer()
                if chosen {
                    Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(chosen ? .isSelected : [])
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
