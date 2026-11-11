import SwiftUI

/// A dive site's Regulars board (dive-sites/site-regulars), drawn from the states `DiveSiteRegularsPresenter` publishes, a page at a
/// time: the scope as a segmented choice and the window as a filter menu, each diver with their rank, picture, name, the day they reached
/// their count and their dives, opening their profile, and the viewer's own place when it isn't among those loaded. What each choice
/// offers and says, which rank is accented and how an entry reads are Kotlin's (`DiveSiteRegularsTexts`, `DiveSiteRegularsState`), shared
/// with the Compose page. It is pushed over the dive site page.
struct DiveSiteRegularsView: View {
    let state: DiveSiteRegularsState

    var body: some View {
        PagedList(
            state.entries, of: DiveSiteRegularEntry.self, errorKind: .LoadRegulars, id: { $0.athlete.did.value }, keepsTop: true,
            retry: { send(DiveSiteRegularsEvent.Retry.shared) }, loadMore: { send(DiveSiteRegularsEvent.LoadMore.shared) }
        ) {
            BoardFilters {
                BoardSegments(state.scopeChoice) { if let scope = $0 { send(DiveSiteRegularsEvent.ScopeChanged(scope: scope)) } }
            } menus: {
                BoardMenu(state.periodChoice) { if let period = $0 { send(DiveSiteRegularsEvent.PeriodChanged(period: period)) } }
            }
        } row: { entry in
            row(entry)
        } after: {
            if let own = state.viewerEntry {
                YourPlace { row(own) }
            }
        } empty: {
            Text(.diveSiteRegularsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .pageTitle(Text(.diveSiteRegularsTitle(state.name)))
    }

    private func row(_ entry: DiveSiteRegularEntry) -> some View {
        RankRow(
            rank: RankBoardTexts.shared.rank(rank: entry.rank), accented: state.accents(entry: entry), athlete: entry.athlete,
            detail: DiveSiteRegularsTexts.shared.reached(entry: entry),
            value: AppStrings.string(DiveSiteRegularsTexts.shared.visits(entry: entry)),
            opens: { send(DiveSiteRegularsEvent.OpenAthlete(did: entry.athlete.did)) })
    }

    private func send(_ event: any DiveSiteRegularsEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Regulars") {
        SamplePages(Samples.diveSiteRegulars) { state in NavigationStack { DiveSiteRegularsView(state: state) } }
    }

    #Preview("Last year, beyond loading") {
        SamplePages(Samples.diveSiteRegularsVariants) { state in NavigationStack { DiveSiteRegularsView(state: state) } }
    }
#endif
