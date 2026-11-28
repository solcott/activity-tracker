import SwiftUI

/// A range chosen with one slider of two thumbs (ui/ios-design-system), which SwiftUI doesn't have: a track, the span between the
/// thumbs in the accent colour, and two round thumbs drawn as the system slider draws its one. A thumb follows a drag along the track
/// and never passes the other; only the thumbs take a drag, so a vertical drag elsewhere on the row scrolls the form. It reports every
/// change as it moves, as the shared slider does.
///
/// Each thumb is its own adjustable element for VoiceOver, named by `lowerLabel` and `upperLabel`, read with `value(_:)` of its
/// position, and stepped by a hundredth of the range.
///
/// With `precision`, a drag slows as the finger moves below the track (feed/activity-trim): `precision` gives the share of full speed
/// for the points below the track's centre, the thumb then follows that share of the finger's sideways travel from where it was when the
/// share last changed, and `onPrecision` hears each new share and nil when the drag ends.
struct RangeSlider: View {
    let lower: Double
    let upper: Double
    let bounds: ClosedRange<Double>
    let lowerLabel: Text
    let upperLabel: Text
    let value: (Double) -> Text
    var precision: ((CGFloat) -> Double)?
    var onPrecision: (Double?) -> Void = { _ in }
    let onChange: (Double, Double) -> Void

    @Environment(\.isEnabled) private var isEnabled
    /// Each slowed drag's anchor, by thumb (true the lower): where the thumb was and the finger's x when the current share began, and the
    /// share. One per thumb, so two fingers dragging both never move each other's.
    @State private var slowed: [Bool: (value: Double, x: CGFloat, share: Double)] = [:]

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
                thumb(at: lower, width: width, label: lowerLabel, isLower: true) { moved in onChange(min(moved, upper), upper) }
                thumb(at: upper, width: width, label: upperLabel, isLower: false) { moved in onChange(lower, max(moved, lower)) }
            }
            .frame(height: geometry.size.height)
            .coordinateSpace(.named(Self.space))
        }
        .frame(height: Self.thumb + 8)
    }

    /// A thumb at `position`, moved to wherever a drag along the track is, and stepped by VoiceOver.
    private func thumb(
        at position: Double, width: CGFloat, label: Text, isLower: Bool, move: @escaping (Double) -> Void
    ) -> some View {
        Circle()
            .fill(.white)
            .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
            .overlay(Circle().stroke(Color.black.opacity(0.04), lineWidth: 0.5))
            .frame(width: Self.thumb, height: Self.thumb)
            .contentShape(Circle().inset(by: -8))
            .offset(x: x(position, width) - Self.thumb / 2)
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                    .onChanged { drag in
                        guard let precision else {
                            move(self.position(at: drag.location.x, width: width))
                            return
                        }
                        let share = precision(max(drag.location.y - (Self.thumb + 8) / 2, 0))
                        let previous = slowed[isLower]
                        if previous == nil || previous?.share != share {
                            if previous != nil || share != 1 { onPrecision(share) }
                            slowed[isLower] = (position, drag.location.x, share)
                        }
                        guard let anchor = slowed[isLower] else { return }
                        let room = Double(max(width - Self.thumb, 1))
                        let span = bounds.upperBound - bounds.lowerBound
                        let moved = anchor.value + Double(drag.location.x - anchor.x) / room * span * share
                        move(min(max(moved, bounds.lowerBound), bounds.upperBound))
                    }
                    .onEnded { _ in
                        if slowed.removeValue(forKey: isLower) != nil, precision != nil {
                            // The other thumb's share while it's still dragged, else none.
                            onPrecision(slowed.values.first?.share)
                        }
                    }
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
            RangeSlider(
                lower: range.lower, upper: range.upper, bounds: 0...2_400, lowerLabel: Text(verbatim: "Start"),
                upperLabel: Text(verbatim: "End"), value: { Text(verbatim: "\(Int($0)) m") },
                precision: { $0 < 24 ? 1 : 0.25 }
            ) { range = ($0, $1) }
        }
    }
#endif
