import SwiftUI

/// The athlete's starred segments, newest star first (segments/segment-favorites), drawn from the states `StarredSegmentsPresenter`
/// publishes, a page at a time: each segment with its name and its length and type, opening it, and a filled star that unstars it; a
/// star whose segment is gone, which opens nothing and can still be unstarred. What a row says, when a star opens or unstars, and the
/// notice that the session must sign in again are Kotlin's (`StarredSegmentsTexts`, `StarredSegmentsState`), shared with the Compose
/// page. It is pushed over the athlete's own profile.
struct StarredSegmentsView: View {
    let state: StarredSegmentsState
    @Environment(\.stats) private var stats

    var body: some View {
        content.pageTitle(Text(.starredSegmentsTitle))
    }

    @ViewBuilder private var content: some View {
        if let signIn = state.signInError {
            // Nothing loads for a session that must sign in again: the notice is the page, as on the shared one.
            List {
                ErrorMessage(signIn, layout: .row).listStatusRow()
            }
            .listStyle(.insetGrouped)
        } else {
            PagedList(
                state.segments, of: StarredSegment.self, errorKind: .LoadStarredSegments, id: { $0.uri.value },
                retry: { send(StarredSegmentsEvent.Retry.shared) }, loadMore: { send(StarredSegmentsEvent.LoadMore.shared) }
            ) {
                // Why the last unstar failed, over the stars, which stay.
                if let error = state.actionError {
                    ErrorMessage(error, layout: .row).listStatusRow()
                }
            } row: { starred in
                row(starred)
            } empty: {
                if state.showsEmpty {
                    Text(.starredSegmentsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                }
            }
        }
    }

    @ViewBuilder private func row(_ starred: StarredSegment) -> some View {
        let unstar = AppIconButton(.star, label: .segmentUnstar, filled: true) {
            send(StarredSegmentsEvent.Unstar(uri: starred.uri))
        }
        .disabled(state.isRemoving(starred: starred))
        if let segment = starred.segment, state.canOpen(starred: starred) {
            AppItemRow(
                Text(verbatim: segment.currentName),
                lines: [Text(StarredSegmentsTexts.shared.summary(segment: segment, units: stats.units))],
                opens: { send(StarredSegmentsEvent.Open(uri: starred.uri)) }
            ) {
                EmptyView()
            } trailing: {
                unstar
            }
        } else {
            // A segment that's gone opens nothing, and its star still unstars it.
            AppItemRow(Text(.starredSegmentUnavailable).foregroundStyle(.secondary)) { EmptyView() } trailing: { unstar }
        }
    }

    private func send(_ event: any StarredSegmentsEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Starred segments") {
        SamplePages(Samples.starredSegments) { state in NavigationStack { StarredSegmentsView(state: state) } }
    }

    #Preview("Gone, unstarring and signing in again") {
        SamplePages(Samples.starredSegmentsVariants) { state in NavigationStack { StarredSegmentsView(state: state) } }
    }
#endif
