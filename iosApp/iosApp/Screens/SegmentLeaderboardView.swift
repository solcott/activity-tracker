import SwiftUI

/// A segment's leaderboard (segments/segment-leaderboards), drawn from the states `SegmentLeaderboardPresenter` publishes, a page at a
/// time: the period and scope as segmented choices, the type and the age, weight and gender brackets as filter menus, each entry with
/// its rank, athlete, day and time, opening the activity, and the viewer's own place when it isn't among those loaded. What each choice
/// offers and says, which ranks are accented and how an entry reads are Kotlin's (`SegmentBoardTexts`, `SegmentLeaderboardState`), shared
/// with the Compose page. It is pushed over the segment page; a new choice starts the board again, under the choices, which stay.
struct SegmentLeaderboardView: View {
    let state: SegmentLeaderboardState
    @Environment(\.stats) private var stats

    var body: some View {
        PagedList(
            state.entries, of: LeaderboardEntry.self, errorKind: .LoadLeaderboard, id: { $0.athlete.did.value }, keepsTop: true,
            retry: { send(SegmentLeaderboardEvent.Retry.shared) }, loadMore: { send(SegmentLeaderboardEvent.LoadMore.shared) }
        ) {
            BoardFilters {
                BoardSegments(state.periodChoice) { if let period = $0 { send(SegmentLeaderboardEvent.PeriodChanged(period: period)) } }
                BoardSegments(state.scopeChoice) { if let scope = $0 { send(SegmentLeaderboardEvent.ScopeChanged(scope: scope)) } }
            } menus: {
                if let type = state.typeChoice {
                    BoardMenu(type) { if let board = $0 { send(SegmentLeaderboardEvent.BoardChanged(board: board)) } }
                }
                BoardMenu(state.ageGroupChoice) { send(SegmentLeaderboardEvent.AgeGroupChanged(ageGroup: $0)) }
                BoardMenu(state.weightClassChoice(units: stats.units)) {
                    send(SegmentLeaderboardEvent.WeightClassChanged(weightClass: $0))
                }
                BoardMenu(state.genderChoice) { send(SegmentLeaderboardEvent.GenderChanged(gender: $0)) }
            }
        } row: { entry in
            row(entry)
        } after: {
            if let own = state.viewerEntry {
                YourPlace { row(own) }
            }
        } empty: {
            Text(.leaderboardEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .pageTitle(Text(verbatim: state.name))
    }

    private func row(_ entry: LeaderboardEntry) -> some View {
        RankRow(
            rank: RankBoardTexts.shared.rank(rank: entry.rank), accented: state.accents(entry: entry), athlete: entry.athlete,
            detail: SegmentBoardTexts.shared.effortDay(entry: entry), value: Stats.duration(seconds: entry.effort.elapsed),
            opens: { send(SegmentLeaderboardEvent.OpenActivity(uri: entry.effort.activity)) })
    }

    private func send(_ event: any SegmentLeaderboardEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Leaderboard") {
        SamplePages(Samples.segmentLeaderboard) { state in NavigationStack { SegmentLeaderboardView(state: state) } }
    }

    #Preview("Filtered, beyond loading") {
        SamplePages(Samples.segmentLeaderboardVariants) { state in NavigationStack { SegmentLeaderboardView(state: state) } }
    }

    #Preview("Filtered, in pounds") {
        NavigationStack { SegmentLeaderboardView(state: Samples.segmentLeaderboardVariants["filtered"]!) }
            .environment(\.stats, Stats(units: .Imperial))
    }
#endif
