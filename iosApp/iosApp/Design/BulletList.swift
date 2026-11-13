import IosShell
import SwiftUI

/// Lines chosen in Kotlin as a bulleted list: the shared mark (`LabelText.bullet`), then each line, which wraps under itself rather than
/// under the mark, as the shared UI's `Bullets` draws it (dev/shared-display-text).
struct BulletList: View {
    let lines: [any AppText]

    var body: some View {
        let mark = Text(LabelText.shared.bullet)
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    mark
                    Text(line)
                }
            }
        }
    }
}
