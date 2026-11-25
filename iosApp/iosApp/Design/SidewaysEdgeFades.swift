import SwiftUI

extension View {
    /// Fades out a sideways scroll view's leading or trailing edge while there is more of it that way, so a column cut off at the edge
    /// reads as more to scroll to, as the shared table's do.
    func sidewaysEdgeFades(_ width: CGFloat = 32) -> some View {
        modifier(SidewaysEdgeFades(width: width))
    }
}

private struct SidewaysEdgeFades: ViewModifier {
    let width: CGFloat
    @State private var more = (leading: false, trailing: false)

    func body(content: Content) -> some View {
        content
            .onScrollGeometryChange(for: [Bool].self) { geometry in
                let x = geometry.contentOffset.x + geometry.contentInsets.leading
                let end = geometry.contentSize.width - geometry.containerSize.width
                return [x > 0.5, x < end - 0.5]
            } action: { _, edges in
                more = (edges[0], edges[1])
            }
            .mask {
                HStack(spacing: 0) {
                    LinearGradient(colors: [more.leading ? .clear : .black, .black], startPoint: .leading, endPoint: .trailing)
                        .frame(width: width)
                    Color.black
                    LinearGradient(colors: [.black, more.trailing ? .clear : .black], startPoint: .leading, endPoint: .trailing)
                        .frame(width: width)
                }
            }
    }
}
