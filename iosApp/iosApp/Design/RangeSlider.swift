import SwiftUI

/// A range chosen with one slider of two thumbs (ui/ios-design-system), which SwiftUI doesn't have: a track, the span between the
/// thumbs in the accent colour, and two round thumbs drawn as the system slider draws its one. A thumb follows a drag along the track
/// and never passes the other; only the thumbs take a drag, so a vertical drag elsewhere on the row scrolls the form. It reports every
/// change as it moves, as the shared slider does.
///
/// Each thumb is its own adjustable element for VoiceOver, named by `lowerLabel` and `upperLabel`, read with `value(_:)` of its
/// position, and stepped by a hundredth of the range.
struct RangeSlider: View {
    let lower: Double
    let upper: Double
    let bounds: ClosedRange<Double>
    let lowerLabel: Text
    let upperLabel: Text
    let value: (Double) -> Text
    let onChange: (Double, Double) -> Void

    @Environment(\.isEnabled) private var isEnabled

    private static let thumb: CGFloat = 28
    private static let track: CGFloat = 4
    private static let space = "rangeSlider"

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color(.systemFill))
                    .frame(height: Self.track)
                    .padding(.horizontal, Self.thumb / 2)
                Capsule()
                    .fill(isEnabled ? Color.accentColor : Color(.tertiaryLabel))
                    .frame(width: max(x(upper, width) - x(lower, width), 0), height: Self.track)
                    .offset(x: x(lower, width))
                thumb(at: lower, width: width, label: lowerLabel) { moved in onChange(min(moved, upper), upper) }
                thumb(at: upper, width: width, label: upperLabel) { moved in onChange(lower, max(moved, lower)) }
            }
            .frame(height: geometry.size.height)
            .coordinateSpace(.named(Self.space))
        }
        .frame(height: Self.thumb + 8)
    }

    /// A thumb at `position`, moved to wherever a drag along the track is, and stepped by VoiceOver.
    private func thumb(at position: Double, width: CGFloat, label: Text, move: @escaping (Double) -> Void) -> some View {
        Circle()
            .fill(.white)
            .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
            .overlay(Circle().stroke(Color.black.opacity(0.04), lineWidth: 0.5))
            .frame(width: Self.thumb, height: Self.thumb)
            .contentShape(Circle().inset(by: -8))
            .offset(x: x(position, width) - Self.thumb / 2)
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                    .onChanged { drag in move(self.position(at: drag.location.x, width: width)) }
            )
            .accessibilityElement()
            .accessibilityLabel(label)
            .accessibilityValue(value(position))
            .accessibilityAdjustableAction { direction in
                let step = (bounds.upperBound - bounds.lowerBound) / 100
                switch direction {
                case .increment: move(min(position + step, bounds.upperBound))
                case .decrement: move(max(position - step, bounds.lowerBound))
                @unknown default: break
                }
            }
    }

    /// Where `position` is drawn, the thumb's centre, along a track `width` wide.
    private func x(_ position: Double, _ width: CGFloat) -> CGFloat {
        let span = bounds.upperBound - bounds.lowerBound
        let fraction = span > 0 ? (position - bounds.lowerBound) / span : 0
        return Self.thumb / 2 + CGFloat(min(max(fraction, 0), 1)) * max(width - Self.thumb, 0)
    }

    /// The position under `x` along a track `width` wide, within the bounds.
    private func position(at x: CGFloat, width: CGFloat) -> Double {
        let room = max(width - Self.thumb, 1)
        let fraction = min(max((x - Self.thumb / 2) / room, 0), 1)
        return bounds.lowerBound + Double(fraction) * (bounds.upperBound - bounds.lowerBound)
    }
}

#if DEBUG
    #Preview("Range slider") {
        @Previewable @State var range = (lower: 480.0, upper: 1_440.0)
        Form {
            RangeSlider(
                lower: range.lower, upper: range.upper, bounds: 0...2_400, lowerLabel: Text(verbatim: "Start"),
                upperLabel: Text(verbatim: "End"), value: { Text(verbatim: "\(Int($0)) m") }
            ) { range = ($0, $1) }
            RangeSlider(
                lower: 0, upper: 2_400, bounds: 0...2_400, lowerLabel: Text(verbatim: "Start"), upperLabel: Text(verbatim: "End"),
                value: { Text(verbatim: "\(Int($0)) m") }, onChange: { _, _ in }
            )
            .disabled(true)
        }
    }
#endif
