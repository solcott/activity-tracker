import IosShell
import OrgJetbrainsKotlinxKotlinxIoBytestring
import SwiftUI
import UIKit

/// Which of the gear form's sheets is open.
enum TypeChoice: Identifiable {
    /// The sports the item is for: each category's bare sports.
    case sports
    /// The activity types new activities start with the item for: every type, sub-sports under their sport.
    case defaultFor

    var id: Self { self }
}

/// The gear form's sports or default-for sheet: its list in a stack of its own, for its title and Done. A tap ticks or unticks at once,
/// as the shared sheet does, so Done only closes it.
struct TypeChoiceSheet: View {
    let choice: TypeChoice
    let state: EditGearState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            list
                .navigationTitle(choice == .sports ? Text(.gearSportsChoose) : Text(.gearDefaultFor))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button(.gearSportsDone) { dismiss() } }
                }
        }
    }

    @ViewBuilder private var list: some View {
        switch choice {
        case .sports:
            TypeChoiceList(
                groups: SportTypeGroups.shared.gearSports.elements(as: SportTypeGroup.self),
                isChosen: { state.isForSport(sport: $0.sport) },
                toggle: { state.eventSink(EditGearEvent.ToggleSport(sport: $0.sport)) })
        case .defaultFor:
            TypeChoiceList(
                groups: SportTypeGroups.shared.allTypes.elements(as: SportTypeGroup.self),
                isChosen: { state.isDefaultFor(type: $0) },
                toggle: { state.eventSink(EditGearEvent.ToggleDefault(type: $0)) })
        }
    }
}

/// Activity types by category (`SportTypeGroups`), each with its icon and a checkmark when chosen. A sport with sub-sports under it
/// (`SportTypeRow.nested`) discloses them, collapsed at first as the shared picker's are; its own row still ticks it.
struct TypeChoiceList: View {
    let groups: [SportTypeGroup]
    let isChosen: (KotlinActivityType) -> Bool
    let toggle: (KotlinActivityType) -> Void
    @State private var expanded: Set<String> = []

    var body: some View {
        List {
            ForEach(groups, id: \.category) { group in
                Section {
                    ForEach(group.rows.elements(as: SportTypeRow.self), id: \.type.key) { row in
                        let nested = row.nested.elements(as: KotlinActivityType.self)
                        if nested.isEmpty {
                            choice(row.type)
                        } else {
                            DisclosureGroup(isExpanded: isExpanded(row.type.sport)) {
                                ForEach(nested, id: \.key) { choice($0) }
                            } label: {
                                choice(row.type)
                            }
                        }
                    }
                } header: {
                    Text(group.label)
                }
            }
        }
    }

    private func isExpanded(_ sport: String) -> Binding<Bool> {
        Binding(
            get: { expanded.contains(sport) },
            set: { open in if open { expanded.insert(sport) } else { expanded.remove(sport) } })
    }

    private func choice(_ type: KotlinActivityType) -> some View {
        let chosen = isChosen(type)
        return Button { toggle(type) } label: {
            HStack(spacing: 12) {
                SportIcon(type).foregroundStyle(.tint)
                Text(LabelText.shared.activityType(type: type)).foregroundStyle(.primary)
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

extension KotlinActivityType {
    /// What tells one type from another in a list.
    var key: String { "\(sport)/\(subSport ?? "")" }
}

/// The gear form's photo at 72 points: the saved photo from its URL, the picked one from its bytes, or the placeholder.
struct GearPhotoView: View {
    let photo: (any GearPhoto)?

    var body: some View {
        Group {
            if let saved = photo as? GearPhoto.Saved {
                RemoteImage(saved.url) { placeholder }
            } else if let picked = photo as? GearPhoto.Picked, let image = UIImage(data: picked.photo.bytes.data) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                placeholder
            }
        }
        .frame(width: 72, height: 72)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityLabel(Text(.gearPhoto))
    }

    private var placeholder: some View {
        Rectangle().fill(Color(.tertiarySystemFill))
    }
}

extension KotlinByteString {
    /// The bytes as Swift's `Data`.
    var data: Data { ExportedKotlinPackages.kotlinx.io.bytestring.toNSData(self) as Data }
}

/// A native form's photo pick (dev/ios-swift-interop): the picked image's bytes, re-encoded, scaled and stripped by Kotlin as the shared
/// pick is, then sent in the form's event. Kotlin's decoder for activity and gear photos can't read a HEIC, so bytes it can't read are
/// tried once more as a JPEG UIKit re-encodes; bytes that aren't a picture at all send nothing. `Photo` is what the compression gives: a
/// gear photo (`PhotoPick`) or a profile photo's JPEG (`AvatarPick`).
struct PhotoPickOf<Photo> {
    let compress: (Data) async -> Photo?

    /// Loads the picked image with `load`, compresses it, and sends it with `send`; nothing when there is none or it isn't a picture.
    func pick(_ load: () async throws -> Data?, send: (Photo) -> Void) async {
        guard let data = try? await load() else { return }
        if let photo = await compress(data) {
            send(photo)
        } else if let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.95), let photo = await compress(jpeg) {
            send(photo)
        }
    }
}

/// The gear form's photo pick, compressed by `compressPhoto`.
typealias PhotoPick = PhotoPickOf<PickedPhoto>

extension PhotoPickOf where Photo == PickedPhoto {
    /// Compresses with `IosApp.compressPhoto`.
    static var app: PhotoPick {
        PhotoPick { data in
            nonisolated(unsafe) let bytes = ExportedKotlinPackages.kotlinx.io.bytestring.toByteString(data as NSData)
            return try? await IosApp.shared.compressPhoto(image: bytes)
        }
    }
}

#if DEBUG
    #Preview("Sports sheet") {
        TypeChoiceSheet(choice: .sports, state: Samples.editGear["loaded"]!)
    }

    #Preview("Default-for sheet") {
        TypeChoiceSheet(choice: .defaultFor, state: Samples.editGear["loaded"]!)
    }
#endif
