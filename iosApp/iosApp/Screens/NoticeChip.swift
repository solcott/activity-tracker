import SwiftUI

/// A line a labeled subject shows above its content (moderation/labels): Kotlin's `NoticeText` in a small rounded chip, red for an error
/// (a takedown). It wraps rather than truncating, since a takedown's notice is a sentence, longer than a phone is wide. The activity card,
/// the activity page, the profile and a comment all show their notices with it.
struct NoticeChip: View {
    let notice: NoticeText

    var body: some View {
        Text(notice.text)
            .font(.caption)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .foregroundStyle(notice.isError ? Color.red : Color.primary)
            .background(
                notice.isError ? Color.red.opacity(0.15) : Color(.secondarySystemFill),
                in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}
