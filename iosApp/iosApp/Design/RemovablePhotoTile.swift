import SwiftUI

/// A form's photo in a `size`-point square with rounded corners and a Remove button in its top corner (an activity's photos, a trail
/// report's): the picture is the caller's, filling the square. `label` names the photo and `removeLabel` its button to VoiceOver.
struct RemovablePhotoTile<Picture: View>: View {
    private let size: CGFloat
    private let cornerStyle: RoundedCornerStyle
    private let inset: CGFloat
    private let label: Text
    private let removeLabel: Text
    private let remove: () -> Void
    private let picture: Picture

    init(
        size: CGFloat, cornerStyle: RoundedCornerStyle = .circular, inset: CGFloat = 4, label: Text, removeLabel: Text,
        remove: @escaping () -> Void, @ViewBuilder picture: () -> Picture
    ) {
        self.size = size
        self.cornerStyle = cornerStyle
        self.inset = inset
        self.label = label
        self.removeLabel = removeLabel
        self.remove = remove
        self.picture = picture()
    }

    var body: some View {
        picture
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: cornerStyle))
            .overlay(alignment: .topTrailing) {
                Button(action: remove) {
                    Image(systemName: "xmark.circle.fill")
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, .black.opacity(0.5))
                        .font(.title3)
                }
                .buttonStyle(.borderless)
                .padding(inset)
                .accessibilityLabel(removeLabel)
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(label)
    }
}
