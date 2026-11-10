import SwiftUI

/// A group of content that belongs together, like a metric with its chart: an inset section on the grouped background, as the shared
/// UI's card is on iOS.
struct AppCard<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// Work in progress with no known end: the system's spinner, small where it sits over content that is already there.
struct AppProgress: View {
    var small = false

    var body: some View {
        ProgressView().controlSize(small ? .small : .regular)
    }
}
