import SwiftUI

/// A segment's Mayors board (segments/segment-mayors), drawn from the states `SegmentMayorsPresenter` publishes, a page at a time: the
/// window, the scope, the type and the age, weight and gender as one row of menus pinned under the bar, each entry with its rank, athlete, the day it reached its count and the
/// count, opening the athlete's profile, the viewer's own marked, and the viewer's own place pinned at the bottom with what it takes to be
/// Mayor when it isn't among those loaded. What each
/// choice offers and says, which rank is accented and how an entry reads are Kotlin's (`SegmentBoardTexts`, `SegmentMayorsState`), shared
/// with the Compose page. It is pushed over the segment page.
struct SegmentMayorsView: View {
    let state: SegmentMayorsState
    @Environment(\.stats) private var stats

    var body: some View {
        let filters = state.filters
        PagedList(
            state.entries, of: MayorEntry.self, errorKind: .LoadMayors, id: { $0.athlete.did.value },
            retry: { send(SegmentMayorsEvent.Retry.shared) }, loadMore: { send(SegmentMayorsEvent.LoadMore.shared) }
        ) {
            EmptyView()
        } row: { entry in
            row(entry)
        } empty: {
            EmptyMessage(.mayorsEmpty)
        }
        .boardChips { SegmentBoardMenus(fields: filters, units: stats.units) }
        .pinnedPlace(state.viewerEntry != nil) {
            if let own = state.viewerEntry { row(own, note: state.gap) }
        }
        .pageTitle(Text(.mayorsTitle(state.name)))
    }

    private func row(_ entry: MayorEntry, note: (any AppText)? = nil) -> some View {
        RankRow(
            rank: RankBoardTexts.shared.rank(rank: entry.rank), accented: state.accents(entry: entry), athlete: entry.athlete,
            detail: SegmentBoardTexts.shared.reached(entry: entry),
            value: AppStrings.string(SegmentBoardTexts.shared.efforts(entry: entry)), own: state.isOwn(entry: entry), note: note,
            crowned: state.crowned(entry: entry),
            opens: { send(SegmentMayorsEvent.OpenAthlete(did: entry.athlete.did)) })
    }

    private func send(_ event: any SegmentMayorsEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Mayors") {
        SamplePages(Samples.segmentMayors) { state in NavigationStack { SegmentMayorsView(state: state) } }
    }

    #Preview("A window and a type, beyond loading") {
        SamplePages(Samples.segmentMayorsVariants) { state in NavigationStack { SegmentMayorsView(state: state) } }
    }
#endif
