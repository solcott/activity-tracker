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
            SportTypeGroupsList(groups: groups, expanded: $expanded) { group in
                Text(group.label)
            } row: { type, _ in
                choice(type)
            }
        }
    }

    private func choice(_ type: KotlinActivityType) -> some View {
        let chosen = isChosen(type)
        return AppCheckRow(checked: chosen, spacing: 12) {
            toggle(type)
        } label: {
            SportIcon(type).foregroundStyle(.tint)
            Text(LabelText.shared.activityType(type: type)).foregroundStyle(.primary)
        }
    }
}

/// The gear form's photo at 72 points: the saved photo from its URL, the picked one from its bytes, or the placeholder.
struct GearPhotoView: View {
    let photo: (any GearPhoto)?

    var body: some View {
        Group {
            if let saved = photo as? GearPhoto.Saved {
                RemoteImage(saved.url, maxSize: CGSize(width: 72, height: 72)) { placeholder }
            } else if let picked = photo as? GearPhoto.Picked {
                PickedPhotoThumbnail(bytes: picked.photo.bytes, size: 72) { placeholder }
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

/// A native form's photo pick (dev/ios-swift-interop): the picked image's bytes compressed for upload, then sent in the form's event.
/// Bytes that aren't a picture send nothing. `Photo` is what the compression gives: an activity or gear photo (`PhotoPick`, compressed
/// in Swift with libwebp through Kotlin's steps) or a profile photo's JPEG (`AvatarPick`, Kotlin's).
struct PhotoPickOf<Photo> {
    let compress: (Data) async -> Photo?

    /// Loads the picked image with `load`, compresses it, and sends it with `send`; nothing when there is none or it isn't a picture.
    func pick(_ load: () async throws -> Data?, send: (Photo) -> Void) async {
        guard let data = try? await load(), let photo = await compress(data) else { return }
        send(photo)
    }
}

/// An activity or gear photo's pick, compressed by `PhotoCompressor`, which reads a HEIC itself (ios-libwebp-photos).
typealias PhotoPick = PhotoPickOf<PickedPhoto>

extension PhotoPickOf where Photo == PickedPhoto {
    /// Compresses with `PhotoCompressor`, off the main actor, as WebP.
    static var app: PhotoPick {
        PhotoPick { data in
            guard let photo = await PhotoCompressor.compress(data) else { return nil }
            return PickedPhoto(
                bytes: photo.bytes.kotlinByteString, mimeType: "image/webp",
                width: Int32(photo.width), height: Int32(photo.height))
        }
    }
}

#if DEBUG
    #Preview("Sports sheet") {
        if let editGear = Samples.editGear["loaded"] {
            TypeChoiceSheet(choice: .sports, state: editGear)
        }
    }

    #Preview("Default-for sheet") {
        if let editGear = Samples.editGear["loaded"] {
            TypeChoiceSheet(choice: .defaultFor, state: editGear)
        }
    }
#endif
