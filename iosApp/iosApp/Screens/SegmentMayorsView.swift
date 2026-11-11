import SwiftUI

/// A segment's Mayors board (segments/segment-mayors), drawn from the states `SegmentMayorsPresenter` publishes, a page at a time: the
/// scope as a segmented choice, the window, the type and the brackets as filter menus, each entry with its rank, athlete, the day it
/// reached its count and the count, opening the athlete's profile, and the viewer's own place when it isn't among those loaded. What each
/// choice offers and says, which rank is accented and how an entry reads are Kotlin's (`SegmentBoardTexts`, `SegmentMayorsState`), shared
/// with the Compose page. It is pushed over the segment page.
struct SegmentMayorsView: View {
    let state: SegmentMayorsState
    @Environment(\.stats) private var stats

    var body: some View {
        PagedList(
            state.entries, of: MayorEntry.self, errorKind: .LoadMayors, id: { $0.athlete.did.value }, keepsTop: true,
            retry: { send(SegmentMayorsEvent.Retry.shared) }, loadMore: { send(SegmentMayorsEvent.LoadMore.shared) }
        ) {
            BoardFilters {
                BoardSegments(state.scopeChoice) { if let scope = $0 { send(SegmentMayorsEvent.ScopeChanged(scope: scope)) } }
            } menus: {
                BoardMenu(state.periodChoice) { if let period = $0 { send(SegmentMayorsEvent.PeriodChanged(period: period)) } }
                if let type = state.typeChoice {
                    BoardMenu(type) { if let board = $0 { send(SegmentMayorsEvent.BoardChanged(board: board)) } }
                }
                BoardMenu(state.ageGroupChoice) { send(SegmentMayorsEvent.AgeGroupChanged(ageGroup: $0)) }
                BoardMenu(state.weightClassChoice(units: stats.units)) { send(SegmentMayorsEvent.WeightClassChanged(weightClass: $0)) }
                BoardMenu(state.genderChoice) { send(SegmentMayorsEvent.GenderChanged(gender: $0)) }
            }
        } row: { entry in
            row(entry)
        } after: {
            if let own = state.viewerEntry {
                YourPlace { row(own) }
            }
        } empty: {
            Text(.mayorsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .pageTitle(Text(.mayorsTitle(state.name)))
    }

    private func row(_ entry: MayorEntry) -> some View {
        RankRow(
            rank: RankBoardTexts.shared.rank(rank: entry.rank), accented: state.accents(entry: entry), athlete: entry.athlete,
            detail: SegmentBoardTexts.shared.reached(entry: entry), value: AppStrings.string(SegmentBoardTexts.shared.efforts(entry: entry)),
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
