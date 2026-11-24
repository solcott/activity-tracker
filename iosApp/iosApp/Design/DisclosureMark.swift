import SwiftUI

/// The system's disclosure mark (ui/ios-design-system): the forward chevron a row that opens something ends with, as a grouped list's
/// navigation row draws it. Decorative: the row's button trait says it opens.
struct DisclosureMark: View {
    var body: some View {
        Image(systemName: "chevron.forward")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.tertiary)
            .accessibilityHidden(true)
    }
}
