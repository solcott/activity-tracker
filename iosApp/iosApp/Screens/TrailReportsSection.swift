import PhotosUI
import SwiftUI

/// What a segment is like now on its native page (segments/trail-reports): the Conditions section between the elevation profile and the
/// boards, and, over the page, the report sheet with adding photos from the viewer's rides and the question before a non-public ride's
/// photo is shared. Every word and condition is Kotlin's (`TrailReportTexts`, `ReportFields`), shared with the Compose page.

/// The Conditions section: the lead tag in large type (Closed in red with its symbol), the riders line, the other tags, the newest note
/// with its author, or that nothing is current and the last report; all of which opens every report. Then the photo strip, See all, and
/// for a viewer who may report, the question after a recent ride with Report conditions and Add photos.
struct TrailConditionsSection: View {
    let fields: TrailReportFields
    let card: ConditionsCard

    var body: some View {
        Section {
            Button {
                send(TrailReportEvent.OpenReports.shared)
            } label: {
                HStack(spacing: 12) {
                    summary.frame(maxWidth: .infinity, alignment: .leading)
                    DisclosureMark()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            let photos = card.shownPhotos
            if !photos.isEmpty {
                TrailPhotoStrip(photos: photos, more: card.morePhotos) { send(TrailReportEvent.OpenPhoto(index: Int32($0))) }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                if let seeAll = card.seeAll {
                    Button {
                        send(TrailReportEvent.OpenPhoto(index: 0))
                    } label: {
                        Text(seeAll)
                    }
                }
            }
            if card.offersReport {
                if let ask = card.ask {
                    Text(ask)
                }
                Button {
                    send(TrailReportEvent.Open.shared)
                } label: {
                    Label {
                        Text(.trailReportConditions)
                    } icon: {
                        Image(systemName: "exclamationmark.bubble")
                    }
                }
                Button {
                    send(TrailReportEvent.OpenAddPhotos.shared)
                } label: {
                    Label {
                        Text(.trailAddPhotos)
                    } icon: {
                        Image(AppIcon.addPhoto)
                    }
                }
            }
        } header: {
            HStack {
                Text(.trailConditionsTitle)
                Spacer()
                if let count = card.count { Text(count) }
            }
        }
    }

    /// The conditions themselves, or that none are current.
    @ViewBuilder private var summary: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let lead = card.lead {
                ConditionLead(text: lead, closed: card.closed, font: .title2.weight(.bold))
            }
            if let line = card.leadLine {
                Text(line).font(.subheadline).foregroundStyle(.secondary)
            }
            if let empty = card.empty {
                Text(empty).font(.headline)
            }
            if let detail = card.emptyDetail {
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
            }
            let tags = card.shownTags
            if !tags.isEmpty {
                ConditionTags(tags: tags)
            }
            if let note = card.note {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(verbatim: note.author).font(.subheadline.weight(.semibold))
                        Text(note.age).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Text(verbatim: note.text).font(.subheadline)
                }
                .padding(.top, 2)
            }
        }
        .padding(.vertical, 4)
    }

    private func send(_ event: any TrailReportEvent) {
        fields.eventSink(event)
    }
}

/// A condition leading a summary: Closed in the error colour with its symbol, so it reads without colour too; anything else plain.
struct ConditionLead: View {
    let text: any AppText
    let closed: Bool
    var font: Font = .body

    var body: some View {
        HStack(spacing: 6) {
            if closed {
                Image(systemName: "xmark.octagon.fill").accessibilityHidden(true)
            }
            Text(text)
        }
        .font(font)
        .foregroundStyle(closed ? AnyShapeStyle(.red) : AnyShapeStyle(.primary))
    }
}

/// Condition tags as neutral tonal chips that wrap.
struct ConditionTags: View {
    let tags: [any AppText]

    var body: some View {
        FlowRow(spacing: 6, lineSpacing: 6) {
            ForEach(Array(tags.enumerated()), id: \.offset) { _, tag in
                // Closed in red with its symbol, as the lead and the Compose chips draw it, so it never reads as just another tag.
                if TrailReportTexts.shared.isClosed(tag: tag) {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark.octagon.fill").accessibilityHidden(true)
                        Text(tag)
                    }
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.red)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.red.opacity(0.15), in: Capsule())
                } else {
                    Text(tag)
                        .font(.footnote)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color(.tertiarySystemFill), in: Capsule())
                }
            }
        }
    }
}

/// Trail photos in a row of squares sharing the width, none larger than `size` (Kotlin gives at most a strip's worth): "+13" over the
/// last when there are more, and a photo under a media label blurred behind its reason until a tap shows it. A tap on a shown photo
/// opens it at its index.
struct TrailPhotoStrip: View {
    let photos: [PhotoThumb]
    var more: (any AppText)?
    var size: CGFloat = 96
    let open: (Int) -> Void

    /// The labelled photos the viewer has chosen to see here.
    @State private var revealed: Set<Int> = []

    var body: some View {
        HStack(spacing: 8) {
            ForEach(Array(photos.enumerated()), id: \.offset) { index, photo in
                Color.clear
                    .aspectRatio(1, contentMode: .fit)
                    .overlay { tile(photo, index: index) }
                    .frame(maxWidth: size)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 10, style: .continuous) }

    @ViewBuilder private func tile(_ photo: PhotoThumb, index: Int) -> some View {
        // The square comes from a clear shape: a filling picture in a flexible frame would size the frame to itself and spill out of the
        // tile.
        let picture = Color.clear
            .overlay { RemoteImage(photo.url, maxSize: CGSize(width: size, height: size)) { Color(.secondarySystemFill) } }
            .clipped()
        if let blur = photo.blur, !revealed.contains(index) {
            Button {
                revealed.insert(index)
            } label: {
                ZStack {
                    picture.blur(radius: 16).accessibilityHidden(true)
                    Color(.secondarySystemBackground).opacity(0.8)
                    VStack(spacing: 2) {
                        Text(blur).font(.caption2.weight(.semibold)).multilineTextAlignment(.center).lineLimit(3)
                        Text(.trailPhotoHidden).font(.caption2).foregroundStyle(.tint)
                    }
                    .padding(4)
                }
                .clipShape(shape)
            }
            .buttonStyle(.borderless)
        } else {
            let last = index == photos.count - 1
            Button {
                open(index)
            } label: {
                picture
                    .overlay {
                        if last, let more {
                            ZStack {
                                Color.black.opacity(0.45)
                                Text(more).font(.title3.weight(.semibold)).foregroundStyle(.white)
                            }
                        }
                    }
                    .clipShape(shape)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(photo.alt.map { Text(verbatim: $0) } ?? Text(.trailPhotosTitle))
            .accessibilityValue(last ? (more.map { Text($0) } ?? Text(verbatim: "")) : Text(verbatim: ""))
            .accessibilityAddTraits(.isImage)
        }
    }
}

extension View {
    /// Shows `ReportFields`' report sheet over the page, SwiftUI's own, sending Close on a swipe down.
    func trailReportSheet(_ fields: TrailReportFields) -> some View {
        sheet(isPresented: .shown(fields.sheet != nil) { fields.eventSink(TrailReportEvent.Close.shared) }) {
            if let sheet = fields.sheet {
                TrailReportSheetView(sheet: sheet, fields: fields)
            }
        }
    }
}

/// The report sheet: "How's the trail?" with what the viewer rode, Cancel and Post report in its bar; the Surface and On the trail tags
/// as checkmark rows (Closed in red), the note with its count, the photos picked with the library's picker and from the viewer's rides
/// (pushed in the sheet's own stack), and that the report is public. Posting a non-public ride's photo asks first.
struct TrailReportSheetView: View {
    let sheet: TrailReportSheet
    let fields: TrailReportFields
    @State private var note: TextFieldModel
    @State private var picked: [PhotosPickerItem] = []
    @State private var preparing = false

    private static let tile: CGFloat = 72

    init(sheet: TrailReportSheet, fields: TrailReportFields) {
        self.sheet = sheet
        self.fields = fields
        _note = State(initialValue: TextFieldModel(sheet.note))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(sheet.subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                tagSection(.trailReportSurface, tags: sheet.shownSurface)
                tagSection(.trailReportOnTrail, tags: sheet.shownOnTrail)
                Section {
                    AppTextField(
                        label: .trailReportNote, model: note, state: sheet.note, layout: .multiline,
                        supportingText: AppStrings.string(sheet.noteCount),
                        isError: !sheet.noteFits)
                }
                photos
                if let error = fields.error {
                    Section { ErrorMessage(error, layout: .row) }
                }
            }
            .disabled(sheet.posting)
            .navigationTitle(Text(.trailReportSheetTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.actionCancel) { send(TrailReportEvent.Close.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if sheet.posting {
                        AppProgress(small: true)
                    } else {
                        Button(.trailReportPost) { send(TrailReportEvent.Post.shared) }.disabled(!sheet.canPost)
                    }
                }
            }
            .navigationDestination(
                isPresented: .shown(fields.addPhotos != nil) { send(TrailReportEvent.CloseAddPhotos.shared) }
            ) {
                if let adding = fields.addPhotos {
                    TrailAddPhotosView(adding: adding, send: send)
                }
            }
        }
        .alert(
            Text(.trailReportShareTitle),
            isPresented: .shown(fields.confirmingShare) { send(TrailReportEvent.ConfirmShare(share: false)) }
        ) {
            Button(.trailReportShareConfirm) { send(TrailReportEvent.ConfirmShare(share: true)) }
            Button(.actionCancel, role: .cancel) { send(TrailReportEvent.ConfirmShare(share: false)) }
        } message: {
            Text(.trailReportShareBody)
        }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            preparing = true
            Task {
                let photos = await PhotoPick.app.pickAll(items)
                preparing = false
                if !photos.isEmpty { send(TrailReportEvent.AddPicked(photos: photos)) }
            }
        }
    }

    /// One group of tags, each a row ticked while chosen; Closed reads in the error colour with its symbol.
    private func tagSection(_ title: LocalizedStringResource, tags: [TagToggle]) -> some View {
        Section {
            ForEach(tags, id: \.value) { tag in
                AppCheckRow(checked: tag.selected) {
                    send(TrailReportEvent.Toggle(value: tag.value))
                } label: {
                    ConditionLead(text: tag.label, closed: tag.warns)
                }
            }
        } header: {
            Text(title)
        }
    }

    /// The photos picked so far, each with Remove; then, while the report can hold more, the library's picker and the viewer's rides.
    private var photos: some View {
        Section {
            let chosen = sheet.shownPhotos
            if !chosen.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(chosen, id: \.key) { photo in chosenTile(photo) }
                    }
                }
                .scrollClipDisabled()
                .padding(.vertical, 4)
            }
            if sheet.canAddPhotos, sheet.room > 0 {
                HStack {
                    PhotosPicker(
                        selection: $picked, maxSelectionCount: Int(sheet.room), matching: .images, preferredItemEncoding: .current
                    ) {
                        Label {
                            Text(.trailPhotosChoose)
                        } icon: {
                            Image(systemName: "photo.on.rectangle")
                        }
                    }
                    .disabled(preparing)
                    Spacer()
                    if preparing { AppProgress(small: true) }
                }
                Button {
                    send(TrailReportEvent.OpenAddPhotos.shared)
                } label: {
                    HStack {
                        Label {
                            Text(.trailPhotosFromRides)
                        } icon: {
                            Image(systemName: "bicycle")
                        }
                        .foregroundStyle(.tint)
                        Spacer()
                        DisclosureMark()
                    }
                }
            }
        } header: {
            Text(.trailPhotosTitle)
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                Text(.trailPhotosLimit)
                Text(.trailReportPublic)
            }
        }
    }

    private func chosenTile(_ photo: ReportPhotoChoice) -> some View {
        RemoteImage(photo.url, maxSize: CGSize(width: Self.tile, height: Self.tile)) { Color(.tertiarySystemFill) }
            .frame(width: Self.tile, height: Self.tile)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(alignment: .topTrailing) {
                Button {
                    send(TrailReportEvent.RemovePhoto(key: photo.key))
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, .black.opacity(0.5))
                        .font(.title3)
                }
                .buttonStyle(.borderless)
                .padding(2)
                .accessibilityLabel(Text(.trailReportRemovePicked))
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(photo.alt.map { Text(verbatim: $0) } ?? Text(.trailPhotosTitle))
    }

    private func send(_ event: any TrailReportEvent) {
        fields.eventSink(event)
    }
}

/// Adding photos from the viewer's own rides on the segment: a section per ride, newest first, titled with its date and name and its
/// visibility, its photos ticked while chosen and dimmed once the report is full; Add in the bar once anything is ticked.
struct TrailAddPhotosView: View {
    let adding: TrailAddPhotos
    let send: (any TrailReportEvent) -> Void

    private static let tile: CGFloat = 96

    var body: some View {
        List {
            if adding.loading {
                AppProgress().frame(maxWidth: .infinity).listRowBackground(Color.clear)
            }
            ForEach(Array(adding.shownRides.enumerated()), id: \.offset) { _, ride in
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: Self.tile), spacing: 8)], alignment: .leading, spacing: 8) {
                        ForEach(ride.shownPhotos, id: \.key) { photo in tile(photo) }
                    }
                    .padding(.vertical, 4)
                } header: {
                    HStack {
                        Text(ride.title)
                        Spacer()
                        Text(ride.visibility)
                    }
                }
            }
        }
        .navigationTitle(Text(.trailPhotosFromRides))
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Text(.trailPhotosLimit)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if let add = adding.addLabel {
                    Button {
                        send(TrailReportEvent.AddTicked.shared)
                    } label: {
                        Text(add)
                    }
                }
            }
        }
    }

    private func tile(_ photo: RidePhoto) -> some View {
        Button {
            send(TrailReportEvent.TogglePhoto(key: photo.key))
        } label: {
            RemoteImage(photo.url, maxSize: CGSize(width: Self.tile, height: Self.tile)) { Color(.tertiarySystemFill) }
                .frame(width: Self.tile, height: Self.tile)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay {
                    if photo.selected {
                        RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(.tint, lineWidth: 3)
                    }
                }
                .overlay(alignment: .topTrailing) {
                    Image(systemName: photo.selected ? "checkmark.circle.fill" : "circle")
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color.accentColor)
                        .font(.title3)
                        .shadow(radius: 1)
                        .padding(4)
                }
                .opacity(photo.enabled ? 1 : 0.4)
        }
        .buttonStyle(.borderless)
        .disabled(!photo.enabled)
        .accessibilityLabel(photo.alt.map { Text(verbatim: $0) } ?? Text(.trailPhotosTitle))
        .accessibilityAddTraits(photo.selected ? .isSelected : [])
    }
}
