import SwiftUI

/// A dive site's page (dive-sites/site-records, dive-sites/site-conditions, dive-sites/site-regulars, dive-sites/site-names), drawn from the
/// states `DiveSitePresenter` publishes: its centre and radius on the native route map, "originally" its old name, the radius in the
/// viewer's units, who created it, OpenStreetMap's credit and the data licence, the status of a site that isn't active with Open the
/// original for a duplicate, the top Regular opening the board, the conditions, the names (`PlaceNamesSection`), then the dives the viewer
/// may see as native activity cards, page by page. Every word and condition is Kotlin's (`DiveSiteTexts`, `PlaceTexts`, `PlaceNameTexts`,
/// `DiveSiteState`), shared with the Compose page. The place-name sheet and its delete confirmation show over it (`placeNames(_:)`).
///
/// Named `DiveSitePage` because `DiveSiteView` is the lexicon class it shows.
struct DiveSitePage: View {
    let state: DiveSiteState

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadDiveSite, retry: retry) {
            // The site is read from the typed getter, never from the content's erased value.
            if let site = state.site {
                loaded(site)
            }
        }
        .pageTitle(Text(verbatim: state.site?.currentName ?? ""))
        .placeNames(state.names)
    }

    private func loaded(_ site: DiveSiteView) -> some View {
        PagedList(
            state.dives, of: ActivityView.self, errorKind: .LoadDiveSiteDives, id: { $0.uri.value }, keepsTop: true,
            sections: { dives in dives.map { PagedSection(id: $0.uri.value, items: [$0]) } }, retry: retryDives,
            loadMore: { send(DiveSiteEvent.LoadMoreDives.shared) }
        ) {
            RefreshStatusView(state: state.content, errorKind: .LoadDiveSite, retry: retry).listStatusRow()
            if let map = state.routeMap {
                Section {
                    RouteMapView(map: map).listRowInsets(EdgeInsets())
                }
            }
            Section {
                DiveSiteAbout(state: state, site: site, openOriginal: { send(DiveSiteEvent.OpenOriginal.shared) })
            }
            if let regulars = state.regulars {
                Section {
                    let texts = DiveSiteTexts.shared
                    LeaderCardView(
                        title: texts.regularsTitle, leader: regulars.leader?.athlete,
                        value: regulars.leader.map { DiveSiteRegularsTexts.shared.visits(entry: $0) }, none: texts.regularsNone,
                        own: regulars.viewer.map { texts.yourVisits(entry: $0) }
                    ) { send(DiveSiteEvent.OpenRegulars.shared) }
                }
            }
            if let conditions = state.conditions {
                ConditionsSection(conditions: conditions)
            }
            PlaceNamesSection(
                fields: state.names, proposals: state.proposals.elements(as: SegmentNameProposalView.self),
                history: state.history.elements(as: SegmentNameAdoption.self), canPropose: state.canPropose, canVote: state.canVote)
            // The dives' heading, over their cards.
            Section {
            } header: {
                Text(DiveSiteTexts.shared.dives)
            }
        } row: { dive in
            card(dive)
        } empty: {
            Text(DiveSiteTexts.shared.noDives).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
    }

    /// A dive as the shared page shows it: no author link and no Like, its photos opening the dive as the card does.
    private func card(_ dive: ActivityView) -> some View {
        let uri = dive.uri.value
        return ActivityCardView(
            content: ActivityCardContent.Companion.shared.of(activity: dive, viewerDid: state.viewerDid),
            authorClickable: false,
            revealed: state.revealed.contains(key: uri),
            likeable: false,
            send: { event in
                if event is ActivityListEvent.Open || event is ActivityListEvent.OpenMedia {
                    send(DiveSiteEvent.OpenDive(uri: dive.uri))
                }
            },
            reveal: { state.revealed.eventSink(uri) }
        )
    }

    private func retry() {
        send(DiveSiteEvent.Retry.shared)
    }

    private func retryDives() {
        send(DiveSiteEvent.RetryDives.shared)
    }

    private func send(_ event: any DiveSiteEvent) {
        state.eventSink(event)
    }
}

/// The site's original name when renamed, its radius, creator, data credit and licence, and its status with what it means.
private struct DiveSiteAbout: View {
    let state: DiveSiteState
    let site: DiveSiteView
    let openOriginal: () -> Void

    @Environment(\.stats) private var stats

    var body: some View {
        let texts = DiveSiteTexts.shared
        VStack(alignment: .leading, spacing: 4) {
            if let original = state.originalName {
                Text(PlaceNameTexts.shared.originally(original: original)).font(.subheadline).foregroundStyle(.secondary)
            }
            Text(texts.radius(site: site, units: stats.units)).font(.headline)
            Text(texts.createdBy(site: site)).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        if let credit = texts.osmCredit(site: site), let url = URL(string: PlaceTexts.shared.OSM_COPYRIGHT_URL) {
            Link(destination: url) { Text(credit) }
        }
        if let license = texts.license(site: site) {
            Text(license).font(.footnote).foregroundStyle(.secondary)
        }
        if let help = texts.statusHelp(site: site) {
            VStack(alignment: .leading, spacing: 2) {
                if let status = texts.status(site: site) {
                    Text(status).font(.subheadline.weight(.semibold)).foregroundStyle(.tint)
                }
                Text(help).font(.footnote).foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
            if texts.offersOriginal(site: site) {
                Button(action: openOriginal) { Text(PlaceTexts.shared.openOriginal) }
            }
        }
    }
}

/// The site's conditions in the viewer's units, with how many dives they come from and, when more than one month has dives, a line a month.
private struct ConditionsSection: View {
    let conditions: DiveSiteConditions

    @Environment(\.stats) private var stats

    var body: some View {
        let texts = DiveSiteTexts.shared
        Section {
            ForEach(Array(texts.conditionLines(conditions: conditions, units: stats.units).enumerated()), id: \.offset) { _, line in
                Text(line)
            }
            ForEach(Array(texts.monthLines(conditions: conditions, units: stats.units).enumerated()), id: \.offset) { _, line in
                Text(line).font(.footnote)
            }
        } header: {
            Text(texts.conditionsTitle)
        } footer: {
            Text(texts.conditionsFrom(conditions: conditions))
        }
    }
}

#if DEBUG
    #Preview("Dive site") {
        SamplePages(Samples.diveSite) { state in NavigationStack { DiveSitePage(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Dive site, beyond loading") {
        SamplePages(Samples.diveSiteVariants) { state in NavigationStack { DiveSitePage(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
